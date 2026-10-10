package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public final class DynamicSpotHUD extends XSDHUD {
    public static final DynamicSpotHUD INSTANCE = new DynamicSpotHUD();
    private static final int CARD_TOP = 15;
    private static final int IDLE_HEIGHT = 28;
    private static final int IDLE_MIN_WIDTH = 210;
    private static final int IDLE_TEXT_LEFT = 34;
    private static final int IDLE_RIGHT_PADDING = 22;
    private static final int STATUS_ICON_SIZE = 12;
    private static final int STATUS_ICON_GAP = 4;
    private static final String SEPARATOR = "  ·  ";
    private static final int TEXT = 0xFFF2F2F5;
    private static final int MUTED = 0xFFB8BBC5;
    private static final int ACCENT = 0xFFA5D1F5;
    private static final int SUCCESS = 0xFF63D08A;
    private static final int WARNING = 0xFFFFB45C;
    private static final int ERROR = 0xFFFF6B78;
    private static final Identifier CLIENT_ICON = Identifier.fromNamespaceAndPath("skydiao", "icon0.png");
    private static final Identifier USER_ICON = statusIcon("user");
    private static final Identifier LINK_ICON = statusIcon("link");
    private static final Identifier REFRESH_ICON = statusIcon("refresh");
    private static final Pattern HYPIXEL = Pattern.compile(
            "(?i)(?<![\\w.-])www\\.hypixel\\.(?:net|com)(?![\\w.-])");
    private static final Queue<Notice> INCOMING = new ConcurrentLinkedQueue<>();
    private static final ArrayDeque<Notice> NOTICES = new ArrayDeque<>();
    private static final DynamicSpotAnimation TAB_ANIMATION = new DynamicSpotAnimation();
    private static float tabProgress;
    private static List<IdlePart> idleParts = List.of();
    private static int idleWidth = IDLE_MIN_WIDTH;
    private static Object lastConnection;
    private static boolean hypixel;

    private DynamicSpotHUD() {
    }

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
            TAB_ANIMATION.reset();
            tabProgress = 0F;
            if (mc.player == null || mc.level == null) clear();
            return;
        }
        // Tab mixins and the HUD share this exact sample, including the closing frame.
        tabProgress = TAB_ANIMATION.sample(isTabRequested(), System.nanoTime());
        updateIdleContent(mc);
        drainNotices();
        expireNotices();
        if (isTabAnimating()) {
            return;
        } else if (!NOTICES.isEmpty()) {
            renderNotice(context, NOTICES.peek());
        } else {
            renderIdle(context);
        }
    }

    public static boolean isTabRequested() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.level != null
                && client.options.keyPlayerList.isDown()
                && (!client.isLocalServer()
                || client.player.connection.getListedOnlinePlayers().size() > 1
                || client.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST) != null);
    }

    public static boolean shouldReplaceVanillaTab() {
        return ConfigManager.dynamicSpot.getValue() && isTabAnimating();
    }

    public static boolean shouldKeepTabOpen() {
        return ConfigManager.dynamicSpot.getValue() && !isTabRequested() && tabProgress > 0F;
    }

    public static boolean isTabAnimating() {
        return isTabRequested() || tabProgress > 0F;
    }

    public static float tabProgress() {
        return tabProgress;
    }

    public static float idleCardWidth() {
        return idleWidth;
    }

    private static Identifier statusIcon(String name) {
        return Identifier.fromNamespaceAndPath("skydiao", "textures/gui/dynamic_spot/" + name + ".png");
    }

    private static void updateIdleContent(Minecraft client) {
        String user = client.getUser() == null ? "Player" : client.getUser().getName();
        String server = serverLabel(client);
        int ping = ping(client);
        String pingText = ping < 0 ? "...ms" : ping + "ms";
        String connection = server == null ? "singleplayer" : server;
        String fpsText = Math.max(0, client.getFps()) + " fps";
        int pingColor = server == null || ping < 0 ? TEXT : ping < 100 ? 0xFF80C810
                : ping <= 400 ? 0xFFC87D28 : 0xFFCA3E2E;
        int maxWidth = Math.max(1, client.getWindow().getGuiScaledWidth() - 16);

        // Reserve the icons, separators, ping and FPS before shortening long names/addresses.
        int fixedWidth = IDLE_TEXT_LEFT + IDLE_RIGHT_PADDING + client.font.width("Skydiao")
                + 3 * (STATUS_ICON_SIZE + STATUS_ICON_GAP + client.font.width(SEPARATOR))
                + client.font.width(fpsText)
                + (server == null ? 0 : client.font.width(pingText) + client.font.width(" to "));
        int available = Math.max(0, maxWidth - fixedWidth);
        int userWidth = client.font.width(user);
        int connectionWidth = client.font.width(connection);
        if (userWidth + connectionWidth > available) {
            int userBudget = Math.min(userWidth, Math.max(available - connectionWidth, Math.round(available * 0.4F)));
            user = fit(user, userBudget);
            connection = fit(connection, Math.max(0, available - client.font.width(user)));
        }

        List<IdlePart> parts = new ArrayList<>();
        parts.add(textPart(client, "Skydiao", TEXT));
        parts.add(textPart(client, SEPARATOR, MUTED));
        parts.add(iconPart(USER_ICON, TEXT));
        parts.add(textPart(client, user, TEXT));
        parts.add(textPart(client, SEPARATOR, MUTED));
        parts.add(iconPart(LINK_ICON, pingColor));
        if (server != null) {
            parts.add(textPart(client, pingText, pingColor));
            parts.add(textPart(client, " to ", TEXT));
        }
        parts.add(textPart(client, connection, TEXT));
        parts.add(textPart(client, SEPARATOR, MUTED));
        parts.add(iconPart(REFRESH_ICON, TEXT));
        parts.add(textPart(client, fpsText, TEXT));
        idleParts = List.copyOf(parts);
        int contentWidth = parts.stream().mapToInt(IdlePart::width).sum();
        idleWidth = Math.min(maxWidth, Math.max(IDLE_MIN_WIDTH, contentWidth + IDLE_TEXT_LEFT + IDLE_RIGHT_PADDING));
    }

    private static IdlePart textPart(Minecraft client, String text, int color) {
        return new IdlePart(text, null, color, client.font.width(text));
    }

    private static IdlePart iconPart(Identifier icon, int color) {
        return new IdlePart("", icon, color, STATUS_ICON_SIZE + STATUS_ICON_GAP);
    }

    public static void drawTabShell(GuiGraphicsExtractor graphics, int screenWidth, int naturalTop,
                                    int naturalWidth, int naturalHeight) {
        float progress = tabProgress();
        var pose = graphics.pose();
        pose.pushMatrix();
        HudOffsetAndScale settings = hudOffsetAndScaleMap.get("dynamic_spot");
        if (settings != null) {
            // Start at the edited HUD position and finish at the normal Tab position.
            pose.scale(settings.scale() + (1F - settings.scale()) * progress);
            pose.translate(settings.x() * (1F - progress), settings.y() * (1F - progress));
        }
        float targetWidth = Math.min(screenWidth - 16F, Math.max(IDLE_MIN_WIDTH, naturalWidth + 18));
        float targetHeight = Math.max(IDLE_HEIGHT, naturalHeight + 12);
        float currentWidth = idleCardWidth() + (targetWidth - idleCardWidth()) * progress;
        float currentHeight = IDLE_HEIGHT + (targetHeight - IDLE_HEIGHT) * progress;
        float radius = Math.min(currentHeight / 2F, 14F + progress);
        int x = Math.round((screenWidth - currentWidth) / 2F);
        int y = CARD_TOP;
        DynamicSpotSurface.draw(graphics, x, y, currentWidth, currentHeight, radius);
        // Restore the watermark while the same shell contracts around it.
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
        TAB_ANIMATION.reset();
        lastConnection = null;
        hypixel = false;
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
        int x = Math.round((Minecraft.getInstance().getWindow().getGuiScaledWidth() - idleWidth) / 2F);
        DynamicSpotSurface.draw(graphics, x, CARD_TOP, idleWidth, IDLE_HEIGHT, IDLE_HEIGHT / 2F);
        drawIdleContent(graphics, x, CARD_TOP, idleWidth, 1F);
    }

    private static void renderNotice(GuiGraphicsExtractor graphics, Notice notice) {
        Minecraft client = Minecraft.getInstance();
        int maxWidth = client.getWindow().getGuiScaledWidth() - 16;
        int cardWidth = Math.min(maxWidth,
                Math.max(170, Math.max(client.font.width(notice.title), client.font.width(notice.detail)) + 56));
        int x = (client.getWindow().getGuiScaledWidth() - cardWidth) / 2;
        DynamicSpotSurface.draw(graphics, x, CARD_TOP, cardWidth, 39, 10);
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
            // Sample the complete transparent logo, including during the Tab crossfade.
            graphics.blit(RenderPipelines.GUI_TEXTURED, CLIENT_ICON, x + 8, y + 5,
                    0, 0, 18, 18, 64, 64, 64, 64, (alpha << 24) | 0xFFFFFF);
            int partX = x + IDLE_TEXT_LEFT;
            for (IdlePart part : idleParts) {
                int color = (alpha << 24) | (part.color() & 0xFFFFFF);
                if (part.icon() == null) {
                    graphics.text(client.font, Component.literal(part.text()), partX, y + 9, color);
                } else {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, part.icon(), partX, y + (IDLE_HEIGHT - STATUS_ICON_SIZE) / 2,
                            0, 0, STATUS_ICON_SIZE, STATUS_ICON_SIZE, 64, 64, 64, 64, color);
                }
                partX += part.width();
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

    private static String serverLabel(Minecraft client) {
        if (client.hasSingleplayerServer()) {
            lastConnection = null;
            hypixel = false;
            return null;
        }
        if (client.getConnection() == null) {
            lastConnection = null;
            hypixel = false;
            return "server";
        }
        if (lastConnection != client.getConnection()) {
            lastConnection = client.getConnection();
            hypixel = false;
        }
        if (!hypixel && client.level != null) {
            hypixel = hasHypixelFooter(client.level.getScoreboard(), client.player.getScoreboardName());
        }
        if (hypixel) return "mc.hypixel.net";
        return client.getCurrentServer() == null ? "server" : client.getCurrentServer().ip;
    }

    private static boolean hasHypixelFooter(Scoreboard scoreboard, String playerName) {
        PlayerTeam team = playerName == null ? null : scoreboard.getPlayersTeam(playerName);
        Objective objective = null;
        if (team != null) {
            DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
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

    private record IdlePart(String text, Identifier icon, int color, int width) {
    }
}
