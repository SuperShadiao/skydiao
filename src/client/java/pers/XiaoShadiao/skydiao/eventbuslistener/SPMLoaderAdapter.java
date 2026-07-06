package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.ProjectileCapability;
import com.micaftic.morpher.capability.ProjectileModelCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.api.network.fabric.YSMPayload;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.resource.models.ModelProperties;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.LocalModelSelectionStore;
import com.micaftic.morpher.util.data.OrderedStringMap;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.apache.commons.lang3.StringUtils;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SPMLoaderAdapter extends AbstractListener implements ICustomSkinModelLoader {

    private final boolean support = FabricLoader.getInstance().isModLoaded("sparkle_morpher");

    public boolean isSupportYSM() {
        return support;
    }

    private String currentModelId;
    private String currentTextureId;

    private boolean isFlying = false;

    private final Map<String, Pair<String, String>> name2Id = new HashMap<>();

    @Override
    public String getListenerName() {
        return "SPMLoaderAdapter";
    }

    @Override
    public void registerListeners() {
        if (!support) {
            return;
        }
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
        CustomFabricEvents.CLIENT_SEND_PACKET_EVENT.register(this::onSendPacket);
    }

    private void onSendPacket(Packet<?> packet) {
        if (packet instanceof ServerboundCustomPayloadPacket(CustomPacketPayload payload)) {
            // logger.info("Send Custom Payload: " + payload.type().id());
            if (payload.type().id().getNamespace().equals("sparkle_morpher")) {
                if (payload instanceof YSMPayload(FriendlyByteBuf buf)) {
                    FriendlyByteBuf copy = new FriendlyByteBuf(buf.copy());
                    long id = copy.readUnsignedByte();
                    if (id == 7) {
                        byte[] remaining = new byte[copy.readableBytes()];
                        copy.readBytes(remaining);
                        trySendToIRC(new IRCModelPacket(currentModelId, currentTextureId, Base64.getEncoder().encodeToString(remaining), "play_animation").toIRCPacket());
                    }
                }
            }
        }
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        name2Id.clear();
        switchToModel(currentModelId, currentTextureId);
    }

    private void onTick(Minecraft mc) {
        if (!StatusManager.get().hasStatus()) return;

        if (ClientModelManager.getSyncStatus().getCurrentState() != ClientModelManager.SyncState.IDLE) {
            ClientModelManager.onSyncConnected();
        }
        if (!NetworkHandler.isClientConnected()) {
            NetworkHandler.markClientHandshakeComplete();
        }

        if (mc.player == null) return;
        PlayerCapability.get(mc.player).ifPresent(cap -> {
            String modelId = cap.getModelId().toLowerCase();
            String currentTextureName = cap.getCurrentTextureName().toLowerCase();
            if (!modelId.equals(currentModelId) || !currentTextureName.equals(currentTextureId)) {
                currentModelId = modelId;
                currentTextureId = currentTextureName;
                logger.info("Switched Model to {} | {}", currentModelId, currentTextureId);
                switchToModel(modelId, currentTextureName);
            }
        });
        if (mc.level == null) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof RemotePlayer player) {
                PlayerCapability.get(player).ifPresent(cap -> {
                    Pair<String, String> id = name2Id.get(player.getName().getString());
                    if (id != null) {
                        String currentId = cap.getModelId();
                        String currentTextureName = cap.getCurrentTextureName();
                        if (ClientModelManager.getModelContext(id.first()).isPresent()) {
                            if (!currentId.equals(id.first()) || !currentTextureName.equals(id.second())) {
                                cap.initModelWithTexture(id.first(), id.second());
                            }
                        }
                    }
                });
            } else if(entity instanceof Projectile projectile) {
                ProjectileModelCapability.get(projectile).ifPresent(cap -> {
                    Entity owner = projectile.getOwner();
                    if(owner instanceof AbstractClientPlayer player) {
                        Pair<String, String> id = name2Id.get(player.getName().getString());
                        if(player == mc.player) id = Pair.of(currentModelId, currentTextureId);
                        if(id != null) {
                            String mapModelId = id.first();
                            String capModelId = cap.getOwnerModelId();
                            Optional<ModelAssembly> context = ClientModelManager.getModelContext(id.first());
                            if (context.isPresent()) {
                                if (!mapModelId.equals(capModelId)) {
                                    ModelInfoCapability.get(player).ifPresent(modelInfo -> {
                                        modelInfo.withMolangVars(molangVars -> {
                                            cap.setModel(mapModelId, molangVars);
                                            ProjectileCapability.get(projectile).ifPresent(cap2 -> {
                                                cap2.updateModelId(mapModelId);
                                                Int2FloatOpenHashMap floatMap = new Int2FloatOpenHashMap();
                                                molangVars.object2FloatEntrySet().fastForEach(entry -> floatMap.put(StringPool.computeIfAbsent(entry.getKey()), entry.getFloatValue()));
                                                cap2.setFloatProperties(floatMap);
                                            });
                                        });
                                    });
                                }
                            }
                        }
                    }
                });
            }
        }

        boolean flyChanged = false;
        if (mc.player.getAbilities().flying) {
            if (!isFlying) {
                isFlying = true;
                flyChanged = true;
            }
        } else if (isFlying) {
            isFlying = false;
            flyChanged = true;
        }
        if(flyChanged) {
            trySendToIRC(new IRCModelPacket(currentModelId, currentTextureId, String.valueOf(isFlying), "play_fly_animation").toIRCPacket());
        }

        restoreModelSelection();
    }

    private boolean onPacket(Packet<?> packet, PacketListener listener, PacketProcessor packetProcessor) {
        return false;
    }

    public void handleIRCPacket(ChatPacket packet) {
        IRCModelPacket model = IRCModelPacket.fromIRCPacket(packet);
        switch (model.action) {
            case "upload" -> uploadModelToIRC(model.modelId);
            case "download" -> loadModel(Base64.getDecoder().decode(model.data), model.modelId);
            case "switch" -> {
                name2Id.put(packet.sender, Pair.of(model.modelId.toLowerCase(), model.textureId));
                if (ClientModelManager.getModelContext(model.modelId.toLowerCase()).isEmpty()) {
                    ChatPacket downloadPacket = new IRCModelPacket(model.modelId.toLowerCase(), "", "", "download").toIRCPacket();
                    trySendToIRC(downloadPacket);
                }
            }
            case "play_animation" -> {
                if(mc.level == null) return;
                byte[] decode = Base64.getDecoder().decode(model.data);
                // System.out.println(Arrays.toString(decode));
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decode));
                int animationIndex = buf.readVarInt();
                String category = buf.readUtf();
                // System.out.println(category);
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if(entity instanceof AbstractClientPlayer player) {
                        if(packet.sender.equals(player.getName().getString())) {
                            PlayerCapability.get(player).ifPresent(cap -> {
                                ModelProperties modelProperties = cap.getModelAssembly().getModelData().getModelProperties();
                                Map<String, OrderedStringMap<String, String>> extraAnimationClassify = modelProperties.getExtraAnimationClassify();
                                OrderedStringMap<String, String> extraAnimations;
                                if (StringUtils.isNotBlank(category) && extraAnimationClassify.containsKey(category)) {
                                    extraAnimations = extraAnimationClassify.get(category);
                                } else {
                                    extraAnimations = modelProperties.getExtraAnimation();
                                }

                                if (extraAnimations.size() > animationIndex) {
                                    cap.requestModelSwitch(animationIndex == -1 ? null :extraAnimations.getKeyAt(animationIndex));
                                }
                            });
                        }
                    }
                }
            }
            case "play_fly_animation" -> {
                if(mc.level == null) return;
                boolean isFlying = Boolean.parseBoolean(model.data);
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if(entity instanceof AbstractClientPlayer player) {
                        if(packet.sender.equals(player.getName().getString())) {
                            player.getAbilities().flying = isFlying;
                        }
                    }
                }
            }
        }
    }

    private static void trySendToIRC(ChatPacket packet) {
        if(ChatClientManager.serverAvailable()) ChatClientManager.getChatClient().sender.send(packet);
    }

    private void loadModel(byte[] data, String id) {
        try {
            // 先解析 .ysm 文件
            Method parseMethod = ClientModelManager.class.getDeclaredMethod(
                    "parseImportModel",
                    String.class,
                    byte[].class
            );
            parseMethod.setAccessible(true);
            RawYsmModel rawModel;
            try {
                rawModel = (RawYsmModel) parseMethod.invoke(null, id + ".ysm", data);
            } catch(Throwable e) {
                logger.warn("尝试YSM格式失败: " + e + ", 尝试使用ZIP");
                rawModel = (RawYsmModel) parseMethod.invoke(null, id + ".zip", data);
            }
            // 加载到内存
            Method loadMethod = ClientModelManager.class.getDeclaredMethod(
                    "loadLocalModel",
                    String.class,
                    RawYsmModel.class
            );
            loadMethod.setAccessible(true);
            loadMethod.invoke(null, id.toLowerCase(), rawModel);

            ToolList.printChatMessage(Component.literal("§a[小沙雕] 从IRC下载了一个YSM模型: §e" + id));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] 注意, 模型文件不会写到你的磁盘, 意味着你可以在模型列表看到该玩家的模型, 但重启后资源将被释放! 若你想要对方的模型, 请找他手动索取!"));
            logger.info("加载了一个YSM模型: {}", id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void uploadModelToIRC(String modelId) {
        modelId = modelId.toLowerCase();
        String finalModelId = modelId;

        ClientModelManager.getLocalModelSourcePath(modelId).ifPresent(path -> {
            try {
                byte[] bytes = Files.readAllBytes(path);
                String base64 = Base64.getEncoder().encodeToString(bytes);
                ChatPacket packet = new IRCModelPacket(finalModelId, "", base64, "upload").toIRCPacket();
                trySendToIRC(packet);
                switchToModel(currentModelId, currentTextureId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void resendSwitchPacket() {
        switchToModel(currentModelId, currentTextureId);
    }

    private void switchToModel(String modelId, String textureId) {
        ChatPacket packet = new IRCModelPacket(modelId, textureId, "", "switch").toIRCPacket();
        trySendToIRC(packet);
    }

    public record IRCModelPacket(String modelId, String textureId, String data, String action) {

        public String toPayload() {
            JsonObject jo = new JsonObject();
            jo.addProperty("modelId", modelId);
            jo.addProperty("textureId", textureId);
            jo.addProperty("data", data);
            jo.addProperty("action", action);
            return jo.toString();
        }

        public ChatPacket toIRCPacket() {
            ChatPacket packet = new ChatPacket();
            packet.message = toPayload();
            packet.packetType = "ysm";
            packet.initSender();
            return packet;
        }

        public static IRCModelPacket fromPayload(String payload) {
            JsonObject jo = JsonParser.parseString(payload).getAsJsonObject();
            return new IRCModelPacket(jo.get("modelId").getAsString(), jo.get("textureId").getAsString(), jo.get("data").getAsString(), jo.get("action").getAsString());
        }

        public static IRCModelPacket fromIRCPacket(ChatPacket packet) {
            return fromPayload(packet.message);
        }

    }

    public void restoreModelSelection() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        PlayerCapability.get(player).ifPresent(cap -> {
            if (!"default".equals(cap.getModelId())) {
                return;
            }
            org.apache.commons.lang3.tuple.Pair<String, String> persisted = LocalModelSelectionStore.load();
            if (persisted == null) {
                return;
            }
            String modelId = persisted.getLeft();
            String textureId = persisted.getRight();
            if (!ClientModelManager.isLocalOnlyModel(modelId) && !ClientModelManager.getModelAssemblyMap().containsKey(modelId)) {
                return;
            }
            cap.initModelWithTexture(modelId, textureId);
            currentModelId = modelId;
            currentTextureId = textureId;
            logger.info("Switched Model to {} | {}", currentModelId, currentTextureId);
            switchToModel(modelId, textureId);
        });
    }

}
