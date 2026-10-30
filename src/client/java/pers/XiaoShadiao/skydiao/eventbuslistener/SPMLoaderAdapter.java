package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.ProjectileCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.model.catalog.LocalModelCatalog;
import com.micaftic.morpher.core.model.lifecycle.KeyedRequestLeaseRegistry;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.LocalModelSelectionStore;
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
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatClientManager;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;

public class SPMLoaderAdapter extends AbstractListener implements ICustomSkinModelLoader {

    private final boolean support = FabricLoader.getInstance().isModLoaded("sparkle_morpher");

    public boolean isSupportYSM() {
        return support;
    }

    private String currentModelId;
    private String currentTextureId;

    private boolean isFlying = false;
    private int delaySyncTick = 0;

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
    }

    public void startPlayAnimation(String key) {
        if (!StatusManager.get().hasStatus()) return;
        trySendToIRC(new IRCModelPacket(currentModelId, currentTextureId, key, "play_animation").toIRCPacket());
    }

    public void stopPlayAnimation() {
        if (!StatusManager.get().hasStatus()) return;
        trySendToIRC(new IRCModelPacket(currentModelId, currentTextureId, "", "play_animation").toIRCPacket());
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        name2Id.clear();
        switchToModel(currentModelId, currentTextureId);
        delaySyncTick = 40;
    }

    private void onTick(Minecraft mc) {
        if (!StatusManager.get().hasStatus()) return;

        if(delaySyncTick > 0) {
            delaySyncTick--;
            if(delaySyncTick == 0) resendSwitchPacket();
        }

        if (mc.player == null) return;
        PlayerCapability.get(mc.player).ifPresent(cap -> {
            String modelId = parseModelId(cap.getModelId());
            String currentTextureName = cap.getCurrentTextureName();
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
                Pair<String, String> id = name2Id.get(player.getName().getString());
                if (id != null) {
                    PlayerCapability.get(player).ifPresent(cap -> {
                        String currentId = cap.getModelId();
                        String currentTextureName = cap.getCurrentTextureName();
                        if (ClientModelManager.getModelContext(id.first()).isPresent()) {
                            if (!currentId.equals(id.first()) || !currentTextureName.equals(id.second()) || !cap.isModelReady() || !cap.isModelInitialized()) {
                                cap.initModelWithTexture(id.first(), id.second());
                            }
                        }
                    });
                }
            } else if(entity instanceof Projectile projectile) {
                Entity owner = projectile.getOwner();
                if(owner instanceof AbstractClientPlayer player) {
                    Pair<String, String> id = player == mc.player ? Pair.of(currentModelId, currentTextureId) : name2Id.get(player.getName().getString());
                    if(id != null) {
                        String mapModelId = id.first();
                        Optional<ModelAssembly> context = ClientModelManager.getModelContext(id.first());
                        if (context.isPresent()) {
                            ProjectileCapability.get(projectile).ifPresent(cap2 -> {
                                if (!mapModelId.equals(cap2.getModelId())) {
                                    cap2.updateModelId(mapModelId);
                                    Int2FloatOpenHashMap floatMap = new Int2FloatOpenHashMap();
                                    cap2.setFloatProperties(floatMap);
                                }
                            });
                        }
                    }
                };
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
                String modelIdLowerCase = parseModelId(model.modelId);
                name2Id.put(packet.sender, Pair.of(modelIdLowerCase, model.textureId));
                if (ClientModelManager.getModelContext(modelIdLowerCase).isEmpty()) {
                    ChatPacket downloadPacket = new IRCModelPacket(modelIdLowerCase, "", "", "download").toIRCPacket();
                    trySendToIRC(downloadPacket);
                }
            }
            case "play_animation" -> {
                if(mc.level == null) return;
                String animation = model.data;

                logger.info("Animation Name: " + animation);
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if(entity instanceof AbstractClientPlayer player) {
                        if(packet.sender.equals(player.getName().getString())) {
                            PlayerCapability.get(player).ifPresent(cap -> {
                                cap.requestModelSwitch(animation);
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
        ChatClientManager.trySend(packet, false);
    }

    private void loadModel(byte[] data, String id) {
        Throwable anotherException = null;
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
                anotherException = e;
                logger.error("尝试YSM格式失败, 尝试使用ZIP");
                logger.catching(e);
                rawModel = (RawYsmModel) parseMethod.invoke(null, id + ".zip", data);
            }
            // 加载到内存
            String realId = parseModelId(id);
            Method loadMethod;

            loadMethod = ClientModelManager.class.getDeclaredMethod(
                    "loadLocalModel",
                    String.class,
                    RawYsmModel.class,
                    boolean.class,
                    long.class,
                    LocalModelCatalog.Entry.class,
                    KeyedRequestLeaseRegistry.Lease.class
            );
            loadMethod.setAccessible(true);
            loadMethod.invoke(null, realId, rawModel, false, -1, null, null);

            ToolList.printChatMessage(Component.literal("§a[小沙雕] 从IRC下载了一个YSM模型: §e" + realId));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] 注意, 模型文件不会写到你的磁盘, 意味着你可以在模型列表看到该玩家的模型, 但重启后资源将被释放! 若你想要对方的模型, 请找他手动索取!"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c警告: 小沙雕禁止任何18+模型/内容, 如果你遇到对方包含这些内容, 请及时反馈给小沙雕"));
            logger.info("加载了一个YSM模型: {}", realId);
        } catch (Exception e) {
            logger.error("解析从IRC下发的YSM模型失败: {}", e.getMessage());
            logger.catching(e);
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c解析从IRC下发的YSM模型失败: " + e.getMessage()));
            if(anotherException != null) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c另一个错误: " + anotherException.getMessage()));
            }
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c如果问题频繁发生, 请反馈给小沙雕!"));
        }
    }

    private void uploadModelToIRC(String modelId) {
        modelId = parseModelId(modelId);
        String finalModelId = modelId;

        Optional<ModelAssembly> opt = ClientModelManager.getModelContext(modelId);
        String possibleModelId;
        if(!opt.isPresent()) {
            Map<String, ModelAssembly> map = ClientModelManager.getModelAssemblyMap();
        }
        opt.ifPresentOrElse(context -> {
            ClientModelManager.getLocalModelSourcePath(finalModelId).ifPresentOrElse(path -> {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    ChatPacket packet = new IRCModelPacket(finalModelId, "", base64, "upload").toIRCPacket();
                    trySendToIRC(packet);
                    switchToModel(currentModelId, currentTextureId);
                } catch (Exception e) {
                    logger.catching(e);
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c同步§e" + finalModelId + "§c时发生错误. 请将此问题反馈给小沙雕."));
                }
            }, () -> ToolList.printChatMessage(Component.literal("§a[小沙雕] §c无法定位到模型文件§e" + finalModelId + "§c, 无法进行同步上传. 请将此问题反馈给小沙雕.")));
        }, () -> ToolList.printChatMessage(Component.literal("§a[小沙雕] §c无法找到模型§e" + finalModelId + "§c, 无法进行同步上传. 请将此问题反馈给小沙雕.")));
    }

    public void resendSwitchPacket() {
        switchToModel(currentModelId, currentTextureId);
    }

    private void switchToModel(String modelId, String textureId) {
        if (!StatusManager.get().hasStatus() || modelId == null || textureId == null) return;
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
            currentModelId = parseModelId(modelId);
            currentTextureId = textureId;
            logger.info("Switched Model to {} | {}", currentModelId, currentTextureId);
            switchToModel(modelId, textureId);
        });
    }

    private String stripImportExtension(String modelId) {
        String lower = modelId.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel"}) {
            if (lower.endsWith(extension)) {
                return modelId.substring(0, modelId.length() - extension.length());
            }
        }
        return modelId;
    }

    private String normalizeLocalModelId(String modelId) {
        return stripImportExtension(modelId.replace('\\', '/').toLowerCase(Locale.ROOT).replaceAll("/+", "/"));
    }

    private String parseModelId(String modelId) {
        return modelId;// getFileName(stripImportExtension(normalizeLocalModelId(modelId)));
    }

    private String getFileName(String modelId) {
        String[] split = modelId.split("/");
        if(split.length == 0) return modelId;
        return split[split.length - 1];
    }

}
