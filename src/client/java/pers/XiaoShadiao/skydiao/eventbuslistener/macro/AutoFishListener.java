package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class AutoFishListener extends AbstractListener implements IMacro {

    public boolean mythicalFishModeFlag;
    public boolean mythicalFishMode;

    private final AntiAFKThread antiAFKMove = new AntiAFKThread(AntiAFKThread.MOVE);
    private final AntiAFKThread antiAFKJump = new AntiAFKThread(AntiAFKThread.JUMP);
    private final AntiAFKThread antiAFKRotation = new AntiAFKThread(AntiAFKThread.ROTATION);
    private int catchFishCount;

    class AntiAFKThread extends Thread {

        public static final int
                JUMP = 0,
                MOVE = 1,
                ROTATION = 2;

        private final int type;
        private boolean flag;
        public boolean sneakFlag;

        AntiAFKThread(int type) {
            setName("AutoFish AntiAFK Thread");
            start();

            this.type = type;
        }

        @Override
        public void run() {

            while(true) {
                try {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                    }
                    ToolList.getInstance().log.info("AntiAFK called");
                    try {
                        Thread.sleep(4000 + ToolList.getInstance().random.nextInt(3500) + ToolList.getInstance().random.nextInt(500));
                    } catch (InterruptedException e) {
                    }

                    if (isHoldingFishRod() && !InputSimulator.isInventoryOpen()) {
                        if (type == MOVE) {
                            Consumer<Boolean> key = (flag = !flag) ? InputSimulator::setLeft : InputSimulator::setRight;

                            InputSimulator.setShift(true);

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                            }
                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen()) key.accept(true);

                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException e) {
                            }

                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen()) key.accept(false);

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                            InputSimulator.setShift(false);
                        } else if (type == JUMP) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }

                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setJump(true);

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                            }

                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setJump(false);
                        } else if (type == ROTATION) {

                            float tempP = InputSimulator.getPlayerPitch();
                            float tempY = InputSimulator.getPlayerYaw();

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }

                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setPlayerPitch(tempP + 1.3453f * ToolList.getInstance().random.nextInt(3) * (ToolList.getInstance().random.nextBoolean() ? 1 : -1));
                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setPlayerYaw(tempY + 1.3453f * ToolList.getInstance().random.nextInt(3) * (ToolList.getInstance().random.nextBoolean() ? 1 : -1));

                            try {
                                Thread.sleep(2000 + ToolList.getInstance().random.nextInt(3000));
                            } catch (InterruptedException e) {
                            }

                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setPlayerPitch(tempP);
                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setPlayerYaw(tempY);

                        }
                    }
                } catch (Exception e) {

                }

            }
        }
    }

    private boolean isThreadWorking;

    private void triggerFishHook() {
        if(!isThreadWorking) this.interrupt();
    }

    @Override
    public void run() {
        while (true) {
            isThreadWorking = false;
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {}
            try {
                executeThread();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private void executeThread() {

        isThreadWorking = true;
        if (!ConfigManager.autoFish.getValue() || InputSimulator.isInventoryOpen()) return;
        if (ToolList.getInstance().random.nextInt(3) == 1) {
            if(ConfigManager.autoFishAutoJump.getValue()) antiAFKJump.interrupt();
        } else if(ToolList.getInstance().random.nextInt(10) == 5) {
            if(ConfigManager.autoFishAutoMove.getValue()) antiAFKMove.interrupt();
        }
        if(catchFishCount++ % (2 + ToolList.getInstance().random.nextInt(2)) == 0) {
            if(ConfigManager.autoFishAutoRotation.getValue()) antiAFKRotation.interrupt();
        }

        activeThisMacro();
        InputSimulator.singleRightClick();

        while(true) {
            try {
                Thread.sleep(300);
                break;
            } catch (InterruptedException e) {

            }
        }

        InputSimulator.singleRightClick();

        if(!StatusManager.get().isInSkyblock()) {
            for (int i = 0; i < 20; i++) {
                if (mythicalFishModeFlag) break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }

            if (mythicalFishModeFlag) {
                mythicalFishModeFlag = false;
                mythicalFishMode = true;
                int count = 0;
                int currentCount = 10;
                FishingHook fishEntityFlag = mc.player.fishing;
                while (mythicalFishMode && mc.player != null && isHoldingFishRod()) {

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                    count++;

                    FishingHook fishEntity = mc.player.fishing;
                    if (fishEntity != fishEntityFlag) {
                        mythicalFishMode = false;
                        break;
                    }
                    List<ArmorStand> fish = mc.level.getEntitiesOfClass(ArmorStand.class, fishEntity.getBoundingBox().inflate(1.1));
                    fish.sort(Comparator.comparingDouble(e -> e.distanceTo(fishEntity)));
                    boolean flag = false;

                    for (ArmorStand e : fish) {
                        Component component = e.getName();
                        String nameNoColor = ToolList.getInstance().deleteColorCode(component.getString());
                        if (nameNoColor.matches("\\[\\|+\\]")) {
                            for (Component c : component.getSiblings()) {
                                String name = Optional.of(c.getStyle()).map(Style::getColor).map(TextColor::toString).orElse("");
                                if (name.equals("red")) {
                                    if (count != 0 && count % currentCount == 0) {
                                        currentCount = 7 + ToolList.getInstance().random.nextInt(6);
                                        count = 0;
                                        flag = true;
                                    } else {
                                        flag = false;
                                        break;
                                    }
                                    ;
                                }
                                if (name.equals("green")) {
                                    flag = true;
                                }
                            }

                        }
                    }
                    if (flag) {
                        InputSimulator.singleRightClick();
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
                ensureHookSummoned();
            }
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }
        ensureHookSummoned();
    }

    private void ensureHookSummoned() {
        while(isHoldingFishRod() && mc.player.fishing == null) {
            InputSimulator.singleRightClick();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
        }
    }


    private boolean isHoldingFishRod() {
        return mc.player.getMainHandItem().getItem() == Items.FISHING_ROD;
    }

    @Override
    public String getListenerName() {
        return "AutoFishListener";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::worldUnload);
    }

    private void worldUnload(Minecraft mc, ClientLevel level) {
        mythicalFishMode = mythicalFishModeFlag = false;
    }

    private int lastHookEntityId;

    private void onStartClientTick(Minecraft mc) {
        if(mc.player == null || mc.player.fishing == null || mc.level == null) {
            return;
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if(!(entity instanceof ArmorStand armorStand)) continue;

            if(mc.player.fishing.distanceTo(armorStand) < 2) {
                if(armorStand.getName().getString().contains("!!!")) {
                    if (lastHookEntityId != armorStand.getId()) {
                        lastHookEntityId = armorStand.getId();
                        triggerFishHook();
                        return;
                    }
                }
            }
        }
    }

    private boolean isReady;

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(mc.level == null || mc.player == null) {
            isReady = false;
            return false;
        }
        Component title = ToolList.getInstance().tryGetTitleFromPacket(packet);
        if(title != null) {
            String str = ToolList.getInstance().deleteColorCode(title.getString());
            if (str.toLowerCase().matches("(.*)(mythical fish|神话鱼)(.*)")) {
                mythicalFishModeFlag = true;
            }
        } else if(packet instanceof ClientboundMoveEntityPacket movePacket) {
            Entity entity = movePacket.getEntity(mc.level);
            if(entity != null && mc.player.fishing == entity) {
                if(entity.isInLiquid()) {
                    short y = movePacket.getYa();
                    if(y > 0) isReady = true; else if(isReady && y < -300) {
                        isReady = false;
                        triggerFishHook();
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean isMacroActive() {
        return isHoldingFishRod();
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        return mc.player != null && mc.player.fishing == null;
    }

    @Override
    public String getMacroName() {
        return "Auto Fish";
    }
}
