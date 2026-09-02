package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.utils.HeadTextures;
import pers.XiaoShadiao.skydiao.utils.PartyManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.List;

public class DungeonMiscMessageListener extends AbstractListener implements IDungeonListener {

    private long dungeonStartTime = -1;

    private final IntPriorityQueue armorStandDungeonKey = new IntArrayFIFOQueue();
    private ArmorStand currentArmorStandDungeonKey;

    @Override
    public String getListenerName() {
        return "DungeonMiscMessageListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientStartTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.level != null && ConfigManager.dungeonKeyRender.getValue()) {
            RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);

            if (currentArmorStandDungeonKey != null) {
                Vec3 pos = currentArmorStandDungeonKey.position().add(0, 1.2, 0);
                RenderUtils.renderTrace(wr, pos, 1, 0, 0, 1);
                RenderUtils.renderESP(wr, pos, 1, 0, 0, 1, false);
            }

            wr.finishDraw();
        }
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(packet instanceof ClientboundAddEntityPacket addEntityPacket) {
            if (addEntityPacket.getType() == EntityType.ARMOR_STAND) {
                delayTickExecutor.delayExec(() -> armorStandDungeonKey.enqueue(addEntityPacket.getId()), 2);
            }
        }
        return false;
    }

    private void onClientStartTick(Minecraft mc) {
        if(!StatusManager.get().isInDungeon()) return;
        if(mc.player == null || mc.level == null) return;

        // System.out.println(mc.player.getInventory().getItem(8).getItem());
        if(dungeonStartTime == -1 && mc.player.getInventory().getItem(8).getItem() == Items.FILLED_MAP) {
            dungeonStartTime = System.currentTimeMillis();
        }

        if(!ToolList.getInstance().isEntityOnWorld(currentArmorStandDungeonKey)) {
            currentArmorStandDungeonKey = null;
        }

        IntSet delayCheckAgain = new IntLinkedOpenHashSet();
        while(!armorStandDungeonKey.isEmpty()) {
            int armorStandId = armorStandDungeonKey.dequeueInt();
            Entity entity = mc.level.getEntity(armorStandId);
            if(entity instanceof ArmorStand armorStand) {
                ItemStack slot = armorStand.getItemBySlot(EquipmentSlot.HEAD);
                if (slot.getItem() == Items.PLAYER_HEAD) {
                    String base64 = ToolList.getInstance().getSkullBase64(slot);
                    boolean isKey = HeadTextures.WITHER_KEY.equals(base64);

                    boolean bloodKeySus = HeadTextures.BLOOD_KEY.equals(base64);

                    if(bloodKeySus) {
                        if (mc.player.distanceTo(armorStand) > 20) {
                            delayCheckAgain.add(armorStandId);
                        } else {
                            List<ArmorStand> entitiesOfClass = mc.level.getEntitiesOfClass(ArmorStand.class, armorStand.getBoundingBox().inflate(2, 2, 2));
                            if (entitiesOfClass.stream().noneMatch(a -> a.getName().getString().contains("Overflux"))) {
                                isKey = true;
                            }
                        }
                    }

                    if (isKey) {
                        addStarRailNotification("一个钥匙掉落了!", StarRailNotification.Type.success);
                        currentArmorStandDungeonKey = armorStand;
                    }
                }
            }
        }
        for(int armorStandId : delayCheckAgain) {
            armorStandDungeonKey.enqueue(armorStandId);
        }
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        dungeonStartTime = -1;
    }

    private void onChat(Component component, boolean b) {
        if(!StatusManager.get().isInDungeon()) return;

        String msg = ToolList.getInstance().deleteColorCode(component.getString());

        if(dungeonStartTime != -1 && msg.equals("A shiver runs down your spine...")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonBloodRoomTime.getValue().replace("[time]", String.valueOf((System.currentTimeMillis() - dungeonStartTime) / 1000f)));
        } else if(msg.equals("You can no longer consume or splash any potions during the remainder of this Dungeon run!")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonDrinkPotion.getValue());
        } else if(msg.startsWith("PUZZLE FAIL!") || msg.equals("[STATUE] Oruo the Omniscient: Yikes")) {
            addStarRailNotification("好像有人PUZZLE FAIL了呢, Yikes!", StarRailNotification.Type.warning);
        } else if(
                msg.equals("RIGHT CLICK on the BLOOD DOOR to open it. This key can only be used to open 1 door!") ||
                        msg.equals("RIGHT CLICK on a WITHER door to open it. This key can only be used to open 1 door!")
        ) {
            addStarRailNotification("成功拾取钥匙! 右键门来打开它...",  StarRailNotification.Type.success);
        } else if(PartyManager.isInParty() && msg.matches("Starting in \\d seconds?\\.")) {
            List<String> playerList = ToolList.getInstance().fetchScoreboardLinesNoColor();
            List<String> missingPlayers = PartyManager.getPartyMembers().stream().map(PartyManager.Member::name).filter(member -> {
                for (String s : playerList) {
                    if (s.contains(member)) {
                        return false;
                    }
                }
                return true;
            }).toList();
            if(!missingPlayers.isEmpty()) {
                String value = ConfigManager.dungeonMissingPlayer.getValue();
                if(!value.isBlank()) {
                    addStarRailNotification("有玩家未进入当前服务器!", StarRailNotification.Type.warning);
                    sendDungeonF7ChatMessage(value.replace("[players]", String.join(", ", missingPlayers)));
                }
            }
        }
        // sendDungeonF7ChatMessage(ConfigManager.dungeonBloodRoomTime.getValue());;
    }

    @Override
    public int getFloor() {
        return 7;
    }

}
