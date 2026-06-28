package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.resource.models.ModelProperties;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.StringUtils;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import rip.ysm.api.network.fabric.YSMPayload;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class FoxModuleLoaderAdapter extends AbstractListener {

    private final boolean support = FabricLoader.getInstance().isModLoaded("yes_steve_model");

    public boolean isSupportYSM() {
        return support;
    }

    private String currentModelId;
    private String currentTextureId;

    private boolean isFlying = false;

    private final Map<String, Pair<String, String>> name2Id = new HashMap<>();

    @Override
    public String getListenerName() {
        return "FoxModuleLoaderAdapter";
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
            if (payload.type().id().getNamespace().equals("yes_steve_model")) {
                if (payload instanceof YSMPayload(FriendlyByteBuf buf)) {
                    FriendlyByteBuf copy = new FriendlyByteBuf(buf.copy());
                    long id = copy.readUnsignedByte();
                    if (id == 7) {
                        byte[] remaining = new byte[copy.readableBytes()];
                        copy.readBytes(remaining);
                        ChatClientManager.trySendOrWarning(new IRCModelPacket(currentModelId, currentTextureId, Base64.getEncoder().encodeToString(remaining), "play_animation").toIRCPacket());
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
            String modelId = cap.getModelId();
            String currentTextureName = cap.getCurrentTextureName();
            if (!modelId.equals(currentModelId) || !currentTextureName.equals(currentTextureId)) {
                currentModelId = modelId;
                currentTextureId = currentTextureName;
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
            ChatClientManager.trySendOrWarning(new IRCModelPacket(currentModelId, currentTextureId, String.valueOf(isFlying), "play_fly_animation").toIRCPacket());
        }
    }

    private boolean onPacket(Packet<?> packet, PacketListener listener, PacketProcessor packetProcessor) {
        if(packet instanceof ClientboundCustomPayloadPacket(CustomPacketPayload payload)) {

        }

        return false;
    }

    public void handleIRCPacket(ChatPacket packet) {
        IRCModelPacket model = IRCModelPacket.fromIRCPacket(packet);
        switch (model.action) {
            case "upload" -> uploadModelToIRC(model.modelId);
            case "download" -> loadModel(Base64.getDecoder().decode(model.data), model.modelId);
            case "switch" -> {
                name2Id.put(packet.sender, Pair.of(model.modelId, model.textureId));
                if (ClientModelManager.getModelContext(model.modelId).isEmpty()) {
                    ChatPacket downloadPacket = new IRCModelPacket(model.modelId, "", "", "download").toIRCPacket();
                    ChatClientManager.trySendOrWarning(downloadPacket);
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

    private void loadModel(byte[] data, String id) {
        try {
            // 先解析 .ysm 文件
            Method parseMethod = ClientModelManager.class.getDeclaredMethod(
                    "parseImportModel",
                    String.class,
                    byte[].class
            );
            parseMethod.setAccessible(true);
            RawYsmModel rawModel = (RawYsmModel) parseMethod.invoke(null, id + ".ysm", data);

            // 加载到内存
            Method loadMethod = ClientModelManager.class.getDeclaredMethod(
                    "loadLocalModel",
                    String.class,
                    RawYsmModel.class
            );
            loadMethod.setAccessible(true);
            loadMethod.invoke(null, id, rawModel);

            ToolList.printChatMessage(Component.literal("§a[小沙雕] 从IRC下载了一个YSM模型: §e" + id));
            logger.info("加载了一个YSM模型: {}", id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void test() {
//        ClientModelManager.getSelectedModelId();
//        ClientModelManager.getLocalModelSourcePath();
//        // 获取玩家实体的能力组件
//        PlayerCapability.get(null).ifPresent(cap -> {
//            // 应用模型和纹理
//            cap.getSelectedModelId()
//        });
    }

    private void uploadModelToIRC(String modelId) {
        ClientModelManager.getLocalModelSourcePath(modelId).ifPresent(path -> {
            try {
                byte[] bytes = Files.readAllBytes(path);
                String base64 = Base64.getEncoder().encodeToString(bytes);
                ChatPacket packet = new IRCModelPacket(modelId, "", base64, "upload").toIRCPacket();
                ChatClientManager.trySendOrWarning(packet);
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
        ChatClientManager.trySendOrWarning(packet);
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

}
