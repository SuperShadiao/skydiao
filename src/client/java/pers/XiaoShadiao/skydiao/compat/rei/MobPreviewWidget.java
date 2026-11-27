package pers.XiaoShadiao.skydiao.compat.rei;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import me.shedaniel.rei.api.client.gui.widgets.WidgetWithBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;
import pers.XiaoShadiao.skydiao.utils.text.LegacyText;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REI 卡片左侧的怪物预览。
 *
 * <p>优先按 NEU 的 {@code mobs/*.json} 构造实体并渲染；
 * 资源缺失、实体类型未知或当前没有世界时，降级为怪物伪物品图标。
 */
final class MobPreviewWidget extends WidgetWithBounds {

    private static final Logger LOGGER = LogManager.getLogger("XSD MobPreview");
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1_000_000);
    private static final Map<String, MobRenderSpec> SPECS = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> SKIN_TEXTURES = new ConcurrentHashMap<>();

    private final NeuItemRepository.MobDropRecipe mobDrop;
    private final Rectangle bounds;
    private final ItemStack fallback;

    private LivingEntity entity;
    private boolean permanentFailure;

    MobPreviewWidget(NeuItemRepository.MobDropRecipe mobDrop, Rectangle bounds) {
        this.mobDrop = mobDrop;
        this.bounds = new Rectangle(bounds);
        ItemStack fallbackStack;
        try {
            fallbackStack = NeuItemRepository.INSTANCE.find(mobDrop.sourceItem())
                    .map(NeuItemRepository.INSTANCE::createDisplayStack)
                    .orElse(ItemStack.EMPTY);
        } catch (Throwable throwable) {
            fallbackStack = ItemStack.EMPTY;
        }
        this.fallback = fallbackStack;
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public List<GuiEventListener> children() {
        return List.of();
    }

    @Override
    public void render(GuiGraphics graphics, Rectangle ignored, int mouseX, int mouseY, float delta) {
        LivingEntity living = entity();
        if (living == null) {
            renderFallback(graphics);
            return;
        }

        try {
            renderEntity(graphics, living, mouseX, mouseY);
        } catch (Throwable throwable) {
            entity = null;
            permanentFailure = true;
            LOGGER.warn("怪物预览失败: 渲染实体异常, mob={}, render={}",
                    mobDrop.sourceItem(), mobDrop.render(), throwable);
            renderFallback(graphics);
        }
    }

    /**
     * 与 {@code InventoryScreen#extractEntityInInventoryFollowsMouse} 相同的渲染流程，
     * 但按实体包围盒自适应缩放，并对凋零/末影龙做特殊处理，避免只显示头部或被裁切。
     */
    private void renderEntity(GuiGraphics graphics, LivingEntity living, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(living);
        EntityRenderState renderState = renderer.createRenderState(living, 1.0F);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;

        int x0 = bounds.x + 1;
        int y0 = bounds.y + 1;
        int x1 = bounds.x + bounds.width - 1;
        int y1 = bounds.y + bounds.height - 1;

        boolean dragon = living.getType() == EntityTypes.ENDER_DRAGON;
        boolean wither = living.getType() == EntityTypes.WITHER;

        float boxWidth = Math.max(1.0F, x1 - x0);
        float boxHeight = Math.max(1.0F, y1 - y0);
        float entityWidth = Math.max(0.1F, renderState.boundingBoxWidth);
        float entityHeight = Math.max(0.1F, renderState.boundingBoxHeight);

        float entityScale = Math.min(boxWidth / entityWidth, boxHeight / entityHeight)
                * (dragon ? 0.95F : 0.72F);
        if (wither) {
            // WitherBossRenderer 自身还会再放大 2 倍
            entityScale *= 0.5F;
        }
        entityScale = Mth.clamp(entityScale, 2.0F, 40.0F);

        float centerX = (x0 + x1) / 2.0F;
        float centerY = (y0 + y1) / 2.0F;
        float xAngle = dragon ? 0.0F : (float) Math.atan((centerX - mouseX) / 40.0F);
        float yAngle = dragon ? 0.0F : (float) Math.atan((centerY - mouseY) / 40.0F);

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf xRotation = new Quaternionf();
        if (!dragon) {
            xRotation = new Quaternionf().rotateX(yAngle * 20.0F * (float) (Math.PI / 180.0));
            rotation.mul(xRotation);
        }

        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyRot = 180.0F + xAngle * 20.0F;
            livingState.yRot = xAngle * 20.0F;
            livingState.xRot = livingState.pose == Pose.FALL_FLYING ? 0.0F : -yAngle * 20.0F;
            livingState.boundingBoxWidth /= livingState.scale;
            livingState.boundingBoxHeight /= livingState.scale;
            livingState.scale = 1.0F;
        }

        Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F, 0.0F);
        graphics.entity(
                renderState,
                entityScale,
                translation,
                rotation,
                dragon ? null : xRotation,
                x0,
                y0,
                x1,
                y1
        );
    }

    private LivingEntity entity() {
        if (entity != null) {
            return entity;
        }
        if (permanentFailure) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            // REI 可能在世界加载前就构建了显示；不要锁死 resolved，进入世界后重试。
            return null;
        }

        String render = mobDrop.render();
        String cacheKey = NeuItemRepository.INSTANCE.version() + "\u0000" + render;
        MobRenderSpec spec = SPECS.computeIfAbsent(cacheKey, ignored -> MobRenderSpec.parse(render));
        if (spec.failed() || spec.type() == null) {
            if (!NeuItemRepository.INSTANCE.isLoaded()) {
                // 仓库还没加载完，下一次渲染再试。
                return null;
            }
            LOGGER.warn("怪物预览失败: 无法解析 render, mob={}, render={}", mobDrop.sourceItem(), render);
            permanentFailure = true;
            return null;
        }

        try {
            if (spec.type() == EntityTypes.PLAYER) {
                GuiPlayer player = new GuiPlayer(level);
                player.setId(NEXT_ENTITY_ID.incrementAndGet());
                applyModifiers(player, spec);
                entity = player;
            } else {
                // LOAD 会经过 canSpawn 检查；这里显式忽略检查，避免难度/feature 条件影响预览。
                Entity created = spec.type().create(
                        level,
                        new EntitySpawnRequest(EntitySpawnReason.LOAD, true)
                );
                if (!(created instanceof LivingEntity living)) {
                    LOGGER.warn("怪物预览失败: create 返回非 LivingEntity, mob={}, type={}",
                            mobDrop.sourceItem(), spec.type());
                    permanentFailure = true;
                    return null;
                }
                // ClientLevel.getNextEntityId() 返回 0，实体直接用于 GUI 渲染时
                // ItemModelResolver 会因 id == 0 抛 "Tried to access entity ID before ID assignment"。
                living.setId(NEXT_ENTITY_ID.incrementAndGet());
                applyModifiers(living, spec);
                entity = living;
            }

            if (entity != null) {
                entity.setNoGravity(true);
                entity.tickCount = minecraft.player == null ? 0 : minecraft.player.tickCount;
            }
            return entity;
        } catch (Throwable throwable) {
            LOGGER.warn("怪物预览失败: 创建实体异常, mob={}, render={}",
                    mobDrop.sourceItem(), render, throwable);
            entity = null;
            permanentFailure = true;
            return null;
        }
    }

    private void applyModifiers(LivingEntity living, MobRenderSpec spec) {
        for (JsonObject modifier : spec.modifiers()) {
            try {
                applyModifier(living, modifier);
            } catch (Throwable ignored) {
                // 单个外观修饰失败不应影响整个预览
            }
        }
    }

    private void applyModifier(LivingEntity living, JsonObject modifier) {
        String type = string(modifier, "type");
        if (type == null) {
            return;
        }

        switch (type) {
            case "invisible" -> living.setInvisible(true);
            case "equipment" -> applyEquipment(living, modifier);
            case "playerdata" -> applyPlayerData(living, modifier);
            case "age" -> applyAge(living, modifier);
            case "name" -> {
                String name = string(modifier, "name");
                if (name != null) {
                    living.setCustomName(LegacyText.parse(name));
                }
            }
            default -> {
                // riding / charged / witherdata / horse 暂不处理，
                // 它们只影响少量特殊模型，保留降级显示。
            }
        }
    }

    private void applyEquipment(LivingEntity living, JsonObject modifier) {
        setEquipment(living, EquipmentSlot.MAINHAND, string(modifier, "hand"));
        setEquipment(living, EquipmentSlot.HEAD, string(modifier, "helmet"));
        setEquipment(living, EquipmentSlot.CHEST, string(modifier, "chestplate"));
        setEquipment(living, EquipmentSlot.LEGS, string(modifier, "leggings"));
        setEquipment(living, EquipmentSlot.FEET, string(modifier, "feet"));
    }

    private void setEquipment(LivingEntity living, EquipmentSlot slot, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        ItemStack stack = equipmentStack(token);
        if (!stack.isEmpty() && !stack.is(Items.BARRIER)) {
            living.setItemSlot(slot, stack);
            if (mobDrop.sourceItem().contains("CORPSE")) {
                LOGGER.info("Corpse equipment: mob={}, slot={}, token={}, item={}, equippable={}",
                        mobDrop.sourceItem(), slot, token, stack.getItem(),
                        stack.get(DataComponents.EQUIPPABLE));
            }
        } else if (mobDrop.sourceItem().contains("CORPSE")) {
            LOGGER.info("Corpse equipment skipped: mob={}, slot={}, token={}",
                    mobDrop.sourceItem(), slot, token);
        }
    }

    private ItemStack equipmentStack(String token) {
        int hash = token.indexOf('#');
        if (hash > 0) {
            String type = token.substring(0, hash);
            String data = token.substring(hash + 1);
            Item leather = leatherItem(type);
            if (leather != null) {
                ItemStack stack = new ItemStack(leather);
                try {
                    stack.set(DataComponents.DYED_COLOR, new DyedItemColor(Integer.parseInt(data, 16)));
                } catch (NumberFormatException ignored) {
                }
                return stack;
            }
            if ("SKULL".equals(type)) {
                return new ItemStack(Items.PLAYER_HEAD);
            }
            token = type;
        }
        return RecipeStacks.resolve(token);
    }

    private static Item leatherItem(String type) {
        return switch (type) {
            case "LEATHER_HELMET" -> Items.LEATHER_HELMET;
            case "LEATHER_CHESTPLATE" -> Items.LEATHER_CHESTPLATE;
            case "LEATHER_LEGGINGS" -> Items.LEATHER_LEGGINGS;
            case "LEATHER_BOOTS" -> Items.LEATHER_BOOTS;
            default -> null;
        };
    }

    private void applyAge(LivingEntity living, JsonObject modifier) {
        boolean baby = bool(modifier, "baby");
        if (living instanceof AgeableMob ageable) {
            ageable.setAge(baby ? -1 : 1);
        } else if (living instanceof Zombie zombie) {
            zombie.setBaby(baby);
        }
    }

    private void applyPlayerData(LivingEntity living, JsonObject modifier) {
        if (!(living instanceof GuiPlayer player)) {
            return;
        }
        String skin = string(modifier, "skin");
        Identifier texture = skinTexture(skin);
        if (texture == null) {
            return;
        }

        PlayerModelType model = bool(modifier, "slim")
                ? PlayerModelType.SLIM
                : PlayerModelType.WIDE;
        PlayerSkin base = DefaultPlayerSkin.getDefaultSkin();
        player.setSkin(PlayerSkin.insecure(
                new ClientAsset.ResourceTexture(texture),
                base.cape(),
                base.elytra(),
                model
        ));
    }

    private static Identifier skinTexture(String skin) {
        if (skin == null || !skin.startsWith("neurepo:")) {
            return null;
        }
        String path = skin.substring("neurepo:".length());
        String version = NeuItemRepository.INSTANCE.version();
        String cacheKey = version + "\u0000" + path;
        Identifier cached = SKIN_TEXTURES.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        byte[] bytes = NeuItemRepository.INSTANCE.repositoryBytes(path);
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            NativeImage image = NativeImage.read(bytes);
            String texturePath = "mob_skin/" + Integer.toHexString(cacheKey.hashCode());
            Identifier identifier = Identifier.fromNamespaceAndPath("skydiao", texturePath);
            DynamicTexture texture = new DynamicTexture(
                    () -> "Skydiao mob skin " + path,
                    image
            );
            Minecraft.getInstance().getTextureManager().register(identifier, texture);
            SKIN_TEXTURES.put(cacheKey, identifier);
            return identifier;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private void renderFallback(GuiGraphics graphics) {
        graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0x30000000);
        if (!fallback.isEmpty()) {
            graphics.renderItem(
                    fallback,
                    bounds.x + bounds.width / 2 - 8,
                    bounds.y + bounds.height / 2 - 8
            );
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private record MobRenderSpec(JsonObject json, EntityType<?> type, String entityAlias,
                                 List<JsonObject> modifiers, boolean failed) {

        static MobRenderSpec parse(String render) {
            if (render == null || render.isBlank()) {
                return failedSpec();
            }

            if (render.startsWith("@neurepo:")) {
                String path = render.substring("@neurepo:".length());
                JsonObject json = NeuItemRepository.INSTANCE.repositoryJson(path);
                if (json == null) {
                    return failedSpec();
                }
                String entityTypeId = string(json, "entitytype");
                EntityType<?> type = MobEntityTypes.byEntityTypeId(entityTypeId);
                String alias = string(json, "entity");
                if (type == null) {
                    type = MobEntityTypes.byAlias(alias);
                }
                if (type == null) {
                    return failedSpec();
                }
                return new MobRenderSpec(json, type, alias, modifiers(json), false);
            }

            EntityType<?> type = MobEntityTypes.byAlias(render);
            if (type == null) {
                return failedSpec();
            }
            return new MobRenderSpec(null, type, render, List.of(), false);
        }

        private static List<JsonObject> modifiers(JsonObject json) {
            JsonArray array = json.getAsJsonArray("modifiers");
            if (array == null) {
                return List.of();
            }
            List<JsonObject> modifiers = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    modifiers.add(element.getAsJsonObject());
                }
            }
            return List.copyOf(modifiers);
        }

        private static MobRenderSpec failedSpec() {
            return new MobRenderSpec(null, null, null, List.of(), true);
        }
    }

    private static final class GuiPlayer extends RemotePlayer {

        private PlayerSkin skin;

        private GuiPlayer(ClientLevel level) {
            super(level, new GameProfile(
                    UUID.nameUUIDFromBytes("skydiao-mob-preview".getBytes(StandardCharsets.UTF_8)),
                    "MobPreview"
            ));
        }

        @Override
        public PlayerSkin getSkin() {
            if (skin != null) {
                return skin;
            }
            PlayerSkin fallback = super.getSkin();
            return fallback != null ? fallback : DefaultPlayerSkin.getDefaultSkin();
        }

        private void setSkin(PlayerSkin skin) {
            this.skin = skin;
        }
    }
}
