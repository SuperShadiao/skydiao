package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.*;
import org.jetbrains.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.anim.BezierUtil;
import pers.XiaoShadiao.skydiao.utils.renderutils.BlurredRoundedSurface;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class DynamicSpotHUD extends XSDHUD {
    private static final int CARD_TOP = 75;
    private static final int TAB_CARD_TOP = 15;
    private static final int IDLE_HEIGHT = 28;
    private static final int IDLE_MIN_WIDTH = 210;
    private static final int IDLE_TEXT_LEFT = 10;
    private static final int IDLE_RIGHT_PADDING = 10;
    private static final int STATUS_ICON_SIZE = 12;
    private static final int STATUS_ICON_GAP = 4;
    private static final String SEPARATOR = "  ·  ";
    private static final int TEXT = 0xFFF2F2F5;
    private static final int MUTED = 0xFFB8BBC5;
    private static final int ACCENT = 0xFFA5D1F5;
    private static final int SUCCESS = 0xFF63D08A;
    private static final int WARNING = 0xFFFFB45C;
    private static final int ERROR = 0xFFFF6B78;
    private static final Identifier CLIENT_ICON = Identifier.fromNamespaceAndPath("skydiao", "icon.png");
    private static final Identifier USER_ICON = statusIcon("user");
    private static final Identifier LINK_ICON = statusIcon("link");
    private static final Identifier REFRESH_ICON = statusIcon("refresh");
    private static final Identifier FPS_ICON = statusIcon("fps");
    private static final Identifier TPS_ICON = statusIcon("tps");


    private static final Pattern HYPIXEL = Pattern.compile(
            "(?i)(?<![\\w.-])www\\.hypixel\\.(?:net|com)(?![\\w.-])");
    private static final Queue<Notice> INCOMING = new ConcurrentLinkedQueue<>();
    private static final ArrayDeque<Notice> NOTICES = new ArrayDeque<>();
    private static final BezierUtil TAB_ANIMATION = new BezierUtil(3.5F, 0F);
    private static final BezierUtil IDLE_WIDTH_ANIMATION = new BezierUtil(3.5F, IDLE_MIN_WIDTH);
    private static final BezierUtil CARD_WIDTH_ANIMATION = new BezierUtil(3.5F, IDLE_MIN_WIDTH);
    private static final BezierUtil CARD_HEIGHT_ANIMATION = new BezierUtil(3.5F, IDLE_HEIGHT);
    private static float tabProgress;
    private static float tabBaseWidth = IDLE_MIN_WIDTH;
    private static boolean tabMorphActive;
    private static List<IdlePart> idleParts = List.of();

    public static final Identifier MOD_NAME_PART_ID = Identifier.fromNamespaceAndPath("skydiao", "dynamic_spot_mod_name");
    public static final Identifier USERNAME_PART_ID = Identifier.fromNamespaceAndPath("skydiao", "dynamic_spot_username");
    public static final Identifier FPS_PART_ID = Identifier.fromNamespaceAndPath("skydiao", "dynamic_spot_fps");
    public static final Identifier TPS_PART_ID = Identifier.fromNamespaceAndPath("skydiao", "dynamic_spot_tps");
    public static final Identifier SEPARATOR_PART_ID = Identifier.fromNamespaceAndPath("skydiao", "dynamic_spot_separator");

    private static final List<ReplaceIdlePart> replaceIdleParts = new ArrayList<>();

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("skydiao", "dynamic_spot"), this);
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter,
                             boolean force, HudOffsetAndScale settings) {
    }

    @Override
    public String getHudName() {
        return "dynamic_spot";
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        if (!ConfigManager.dynamicSpot.getValue() || mc.player == null || mc.level == null) {
            TAB_ANIMATION.setAndUpdate(0F);
            tabProgress = 0F;
            if (mc.player == null || mc.level == null) clear();
            return;
        }

        TAB_ANIMATION.update(isTabRequested() ? 1F : 0F);
        TAB_ANIMATION.freeze();
        tabProgress = TAB_ANIMATION.get();
        boolean tabMorph = isTabAnimating();
        if (tabMorph && !tabMorphActive) tabBaseWidth = idleCardWidth();
        tabMorphActive = tabMorph;
        updateIdleContent(mc);
        drainNotices();
        expireNotices();
        if (tabMorph) {
            return;
        }
        IslandCard card = IslandCards.active();
        if (card != null) {
            renderCard(context, card);
            return;
        }
        CARD_WIDTH_ANIMATION.update(idleCardWidth());
        CARD_HEIGHT_ANIMATION.update(IDLE_HEIGHT);
        if (!NOTICES.isEmpty()) {
            renderNotice(context, NOTICES.peek());
        } else {
            renderIdle(context);
        }
    }

    private static void renderCard(GuiGraphicsExtractor context, IslandCard card) {
        int targetWidth = Math.max(160, card.width(mc));
        int targetHeight = Math.max(IDLE_HEIGHT, card.height());
        CARD_WIDTH_ANIMATION.update(targetWidth);
        CARD_HEIGHT_ANIMATION.update(targetHeight);
        CARD_WIDTH_ANIMATION.freeze();
        CARD_HEIGHT_ANIMATION.freeze();

        int cardWidth = Math.round(CARD_WIDTH_ANIMATION.get());
        int cardHeight = Math.round(CARD_HEIGHT_ANIMATION.get());
        int x = Math.round((mc.getWindow().getGuiScaledWidth() - cardWidth) / 2F);
        int y = CARD_TOP;
        BlurredRoundedSurface.draw(context, x, y, cardWidth, cardHeight, Math.min(cardHeight / 2F, 14F));
        context.enableScissor(x, y, x + cardWidth, y + cardHeight);
        card.render(context, x, y, 255);
        context.disableScissor();
    }

    public static boolean isTabRequested() {        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.level != null
                && ConfigManager.dynamicSpotReplaceTabList.getValue()
                && client.options.keyPlayerList.isDown()
                && (!client.isLocalServer()
                || client.player.connection.getListedOnlinePlayers().size() > 1
                || client.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST) != null);
    }

    public static boolean shouldReplaceVanillaTab() {
        return ConfigManager.dynamicSpot.getValue() && isTabAnimating();
    }

    public static boolean shouldKeepTabOpen() {
        return ConfigManager.dynamicSpot.getValue() && !isTabRequested() && tabProgress > 0F && ConfigManager.dynamicSpotReplaceTabList.getValue();
    }

    public static boolean isTabAnimating() {
        return isTabRequested() || tabProgress > 0F;
    }

    public static float tabProgress() {
        return tabProgress;
    }

    public static float idleCardWidth() {
        return IDLE_WIDTH_ANIMATION.get();
    }

    public static Identifier statusIcon(String name) {
        return Identifier.fromNamespaceAndPath("skydiao", "textures/gui/dynamic_spot/" + name + ".png");
    }

    private static void updateIdleContent(Minecraft client) {
        String user = client.getUser() == null ? "Player" : client.getUser().getName();
        String fpsText = Math.max(0, client.getFps()) + " FPS";
        String tpsText = AbstractListener.tpsListener.getCurrentFormattedTPS() + " TPS";
        int maxWidth = Math.max(1, client.getWindow().getGuiScaledWidth() - 16);

        List<IdlePart> parts = new ArrayList<>();
        parts.add(iconPart(MOD_NAME_PART_ID, CLIENT_ICON, 18, TEXT, textPart(null, "Skydiao", TEXT, null)));
        parts.add(textPart(SEPARATOR_PART_ID, SEPARATOR, MUTED, null));
        parts.add(iconPart(USERNAME_PART_ID, USER_ICON, TEXT, textPart(null, user, TEXT, null)));
        parts.add(textPart(SEPARATOR_PART_ID, SEPARATOR, MUTED, null));
        parts.add(iconPart(FPS_PART_ID, FPS_ICON, TEXT, textPart(null, fpsText, TEXT, null)));
        parts.add(textPart(SEPARATOR_PART_ID, SEPARATOR, MUTED, null));
        parts.add(iconPart(TPS_PART_ID, TPS_ICON, TEXT, textPart(null, tpsText, TEXT, null)));
        replaceIdleParts.removeIf(entry -> entry.shouldRemove.shouldRemove());
        parts.replaceAll(part -> {
            for (ReplaceIdlePart replaceIdlePart : replaceIdleParts) {
                if (part != null && Objects.equals(replaceIdlePart.targetIdHolder, part.idHolder)) {
                    part = replaceIdlePart.part.getIdlePart(replaceIdlePart.targetIdHolder);
                    if(part == null) return null;
                }
            }
            return part;
        });
        parts.removeIf(Objects::isNull);

        for (int i = parts.size() - 1; i >= 0; i--) {
            if(i == parts.size() - 1 && Objects.equals(parts.get(i).idHolder, SEPARATOR_PART_ID)) {
                parts.remove(i);
            } else if(i > 0 && Objects.equals(parts.get(i).idHolder, SEPARATOR_PART_ID) && Objects.equals(parts.get(i - 1).idHolder, SEPARATOR_PART_ID)) {
                parts.remove(i);
            } else if(i == 0 && Objects.equals(parts.get(i).idHolder, SEPARATOR_PART_ID)) {
                parts.remove(i);
            }
        }

        idleParts = List.copyOf(parts);
        int contentWidth = parts.stream().mapToInt(IdlePart::width).sum();

        float idleAnimationWidth = Math.min(maxWidth, Math.max(IDLE_MIN_WIDTH, contentWidth + IDLE_TEXT_LEFT + IDLE_RIGHT_PADDING));

        if (!tabMorphActive) {
            IDLE_WIDTH_ANIMATION.update(idleAnimationWidth);
            IDLE_WIDTH_ANIMATION.freeze();
        }
    }

    public static IdlePart textPart(Identifier idHolder, String text, int color, IdlePart next) {
        return new IdlePart(idHolder, Component.literal(text), null, color, mc.font.width(text), next);
    }

    public static IdlePart textPart(Identifier idHolder, Component text, int color, IdlePart next) {
        return new IdlePart(idHolder, text, null, color, mc.font.width(text), next);
    }

    public static IdlePart iconPart(Identifier idHolder, Identifier icon, int color, IdlePart next) {
        return new IdlePart(idHolder, Component.empty(), icon, color, STATUS_ICON_SIZE + STATUS_ICON_GAP, next);
    }

    public static IdlePart iconPart(Identifier idHolder, Identifier icon, int iconSize, int color, IdlePart next) {
        return new IdlePart(idHolder, Component.empty(), icon, color, iconSize + STATUS_ICON_GAP, next);
    }

    public static void replaceIdlePart(Identifier targetIdHolder, IdlePart part, long durationMs) {
        long startTime = System.currentTimeMillis();
        replaceIdleParts.add(new ReplaceIdlePart(targetIdHolder, (_) -> part, () -> System.currentTimeMillis() - startTime > durationMs));
    }

    public interface IdlePartSupplier extends Supplier<IdlePart> {
        IdlePart getIdlePart(@Nullable Identifier replaceTargetIdHolder);

        @Override
        default IdlePart get() {
            return getIdlePart(null);
        }
    }

    public interface ShouldRemoveSupplier extends BooleanSupplier {
        boolean shouldRemove();

        @Override
        default boolean getAsBoolean() {
            return shouldRemove();
        }
    }

    public static void replaceIdlePart(Identifier targetIdHolder, IdlePartSupplier part, long durationMs) {
        long startTime = System.currentTimeMillis();
        replaceIdleParts.add(new ReplaceIdlePart(targetIdHolder, part, () -> System.currentTimeMillis() - startTime > durationMs));
    }

    public static void replaceIdlePart(Identifier targetIdHolder, IdlePartSupplier part, ShouldRemoveSupplier shouldRemove) {
        replaceIdleParts.add(new ReplaceIdlePart(targetIdHolder, part, shouldRemove));
    }

    public static void drawTabShell(GuiGraphicsExtractor graphics, int screenWidth, int naturalTop,
                                    int naturalWidth, int naturalHeight) {
        float progress = tabProgress();
        var pose = graphics.pose();
        pose.pushMatrix();
        HudOffsetAndScale settings = hudOffsetAndScaleMap.getOrDefault("dynamic_spot", HudOffsetAndScale.DEFAULT);
        if (settings != null) {

            pose.scale(settings.scale() + (1F - settings.scale()) * progress);
            pose.translate(settings.x() * (1F - progress), (settings.y() + CARD_TOP - TAB_CARD_TOP) * (1F - progress));
        }
        float targetWidth = Math.min(screenWidth - 16F, Math.max(IDLE_MIN_WIDTH, naturalWidth + 18));
        float targetHeight = Math.max(IDLE_HEIGHT, naturalHeight + 12);
        float currentWidth = tabBaseWidth + (targetWidth - tabBaseWidth) * progress;
        float currentHeight = IDLE_HEIGHT + (targetHeight - IDLE_HEIGHT) * progress;
        float radius = Math.min(currentHeight / 2F, 14F + progress);
        int x = Math.round((screenWidth - currentWidth) / 2F);
        int y = TAB_CARD_TOP;
        BlurredRoundedSurface.draw(graphics, x, y, currentWidth, currentHeight, radius);

        float idleOpacity = Math.clamp(1F - progress / 0.18F, 0F, 1F);
        idleOpacity = idleOpacity * idleOpacity * (3F - 2F * idleOpacity);
        drawIdleContent(graphics, x, y, Math.round(currentWidth), idleOpacity);
        graphics.enableScissor(x, y, x + Math.round(currentWidth), y + Math.round(currentHeight));
        pose.pushMatrix();
        float contentScale = Math.max(0.001F, progress);
        pose.translate(screenWidth / 2F * (1F - contentScale), y + (6F - naturalTop) * contentScale);
        pose.scale(contentScale, contentScale);
    }

    public static void finishTabContent(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
        graphics.disableScissor();
        graphics.pose().popMatrix();
    }

    public static void clear() {
        INCOMING.clear();
        synchronized (NOTICES) {
            NOTICES.clear();
        }
        tabProgress = 0F;
        TAB_ANIMATION.setAndUpdate(0F);
    }

    public static void showSuccess(String title, String detail, long durationMs) {
        enqueue(title, detail, SUCCESS, durationMs);
    }

    public static void showWarning(String title, String detail, long durationMs) {
        enqueue(title, detail, WARNING, durationMs);
    }

    public static void showInfo(String title, String detail, long durationMs) {
        enqueue(title, detail, ACCENT, durationMs);
    }

    public static void showModuleNotification(String moduleName, boolean enabled) {
        enqueue("Module Toggled", moduleName + (enabled ? " enabled" : " disabled"),
                enabled ? SUCCESS : ERROR, 2000L);
    }

    private static void enqueue(String title, String detail, int color, long durationMs) {
        if (title == null || title.isBlank()) title = "Skydiao";
        if (detail == null) detail = "";
        String type = color == SUCCESS ? "OK" : color == WARNING ? "!" : color == ERROR ? "X" : "i";
        INCOMING.add(new Notice(title, detail, type, color, System.currentTimeMillis(),
                Math.max(250L, durationMs)));
    }

    private static void drainNotices() {
        Notice notice;
        synchronized (NOTICES) {
            while ((notice = INCOMING.poll()) != null) {
                if (NOTICES.size() >= 8) NOTICES.removeFirst();
                NOTICES.addLast(notice);
            }
        }
    }

    private static void expireNotices() {
        long now = System.currentTimeMillis();
        synchronized (NOTICES) {
            while (!NOTICES.isEmpty() && now - NOTICES.peek().createdAt >= NOTICES.peek().durationMs) {
                NOTICES.removeFirst();
            }
        }
    }

    private static void renderIdle(GuiGraphicsExtractor graphics) {
        float idleWidth = idleCardWidth();
        int x = Math.round((Minecraft.getInstance().getWindow().getGuiScaledWidth() - idleWidth) / 2F);
        BlurredRoundedSurface.draw(graphics, x, CARD_TOP, idleWidth, IDLE_HEIGHT, IDLE_HEIGHT / 2F);
        drawIdleContent(graphics, x, CARD_TOP, Math.round(idleWidth), 1F);
    }

    private static void renderNotice(GuiGraphicsExtractor graphics, Notice notice) {
        Minecraft client = Minecraft.getInstance();
        int maxWidth = client.getWindow().getGuiScaledWidth() - 16;
        int cardWidth = Math.min(maxWidth,
                Math.max(170, Math.max(client.font.width(notice.title), client.font.width(notice.detail)) + 56));
        int x = (client.getWindow().getGuiScaledWidth() - cardWidth) / 2;
        BlurredRoundedSurface.draw(graphics, x, CARD_TOP, cardWidth, 39, 10);
        graphics.text(client.font, Component.literal(notice.type), x + 9, 22, notice.color);
        int textWidth = Math.max(0, cardWidth - 48);
        graphics.text(client.font, Component.literal(fit(notice.title, textWidth)), x + 35, 22, TEXT);
        graphics.text(client.font, Component.literal(fit(notice.detail, textWidth)), x + 35, 34, MUTED);
    }

    private static void drawIdleContent(GuiGraphicsExtractor graphics, int x, int y, int width, float opacity) {
        int alpha = Math.round(255F * opacity);
        if (alpha < 4 || width <= 8) return;
        Minecraft client = Minecraft.getInstance();
        graphics.enableScissor(x + 4, y + 2, x + width - 4, y + IDLE_HEIGHT - 2);
        try {

            int partX = x + IDLE_TEXT_LEFT;
            for (IdlePart part1 : idleParts) {
                IdlePart part = part1;
                do {
                    int color = (alpha << 24) | (part.color() & 0xFFFFFF);
                    if (part.icon() == null) {
                        graphics.text(client.font, part.text(), partX, y + (IDLE_HEIGHT - client.font.lineHeight + 2) / 2, color);
                    } else {
                        int iconSize = part.width - STATUS_ICON_GAP;
                        graphics.blit(RenderPipelines.GUI_TEXTURED, part.icon(), partX, y + (IDLE_HEIGHT - iconSize) / 2,
                                0, 0, iconSize, iconSize, 64, 64, 64, 64, color);
                    }
                    partX += part.width;
                } while ((part = part.next()) != null);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static String fit(String text, int width) {
        Minecraft client = Minecraft.getInstance();
        if (width <= 0) return "";
        if (client.font.width(text) <= width) return text;
        int ellipsisWidth = client.font.width("...");
        if (width < ellipsisWidth) return client.font.plainSubstrByWidth(text, width);
        String shortened = client.font.plainSubstrByWidth(text, width - ellipsisWidth);
        return shortened + "...";
    }

    private static int ping(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return -1;
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
        return info == null ? -1 : Math.max(0, info.getLatency());
    }

    private static boolean hasHypixelFooter(Scoreboard scoreboard, String playerName) {
        PlayerTeam team = playerName == null ? null : scoreboard.getPlayersTeam(playerName);
        Objective objective = null;
        if (team != null) {
            DisplaySlot slot = team.getColor().map(color -> DisplaySlot.valueOf("TEAM_" + color.name())).orElse(DisplaySlot.SIDEBAR);
            if (slot != null) objective = scoreboard.getDisplayObjective(slot);
        }
        if (objective == null) objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return false;
        return scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(15)
                .anyMatch(entry -> {
                    PlayerTeam entryTeam = scoreboard.getPlayersTeam(entry.owner());
                    String text = PlayerTeam.formatNameForTeam(entryTeam, entry.ownerName()).getString();
                    return HYPIXEL.matcher(text).find();
                });
    }

    private record Notice(String title, String detail, String type, int color, long createdAt, long durationMs) {
    }

    public record IdlePart(Identifier idHolder, Component text, Identifier icon, int color, int width, IdlePart next) {

        public int width() {
            if (next != null) return width + next.width();
            return width;
        }

    }

    public record ReplaceIdlePart(Identifier targetIdHolder, IdlePartSupplier part, ShouldRemoveSupplier shouldRemove) {
    }

}
