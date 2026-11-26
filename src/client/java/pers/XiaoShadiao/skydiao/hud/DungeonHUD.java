package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.dungeon.DungeonMapScanListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.dungeon.DungeonScoreListener;
import pers.XiaoShadiao.skydiao.utils.dungeon.DungeonMapUtils;

/**
 * 地牢相关的简易文字 HUD 基类。每个内部类是一个独立的 HUD 元素，可以单独拖动位置/缩放。
 */
public abstract class DungeonHUD extends XSDHUD {

    protected abstract Identifier hudId();

    protected abstract String hudText();

    /** 2x2 排列在地牢地图（0,0~128,128）正下方，每格宽度是地图的一半。 */
    protected int column() {
        return 0;
    }

    protected int row() {
        return 0;
    }

    protected boolean visible() {
        return DungeonScoreListener.isDungeonStarted();
    }

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(hudId(), this);
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        if (!force && !visible()) return;
        String text = hudText();
        int half = 64;
        int x = column() * half + (half - mc.font.width(text)) / 2;
        int y = 134 + row() * 12;
        context.text(mc.font, text, x, y, 0xFFFFFFFF, true);
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force,
                             HudOffsetAndScale settings) {
    }

    @Override
    public @Nullable String getHudName() {
        return hudId().getPath();
    }

    /** 地牢分数：000 (S)。 */
    public static class ScoreHUD extends DungeonHUD {

        @Override
        protected Identifier hudId() {
            return Identifier.fromNamespaceAndPath("skydiao", "dungeon_score");
        }

        @Override
        protected String hudText() {
            return DungeonScoreListener.formatScore();
        }

        @Override
        protected boolean visible() {
            return super.visible() && ConfigManager.dungeonScoreHud.getValue();
        }
    }

    /** 墓穴数量：Crypts: 3/5。 */
    public static class CryptsHUD extends DungeonHUD {

        @Override
        protected Identifier hudId() {
            return Identifier.fromNamespaceAndPath("skydiao", "dungeon_crypts");
        }

        @Override
        protected String hudText() {
            int value = Math.min(DungeonScoreListener.getCrypts(), 5);
            String progress = value >= 5 ? "§a" + value + "/5" : "§f" + value + "§7/5";
            return "§eCrypts: " + progress;
        }

        @Override
        protected int column() {
            return 1;
        }

        @Override
        protected boolean visible() {
            return super.visible() && ConfigManager.dungeonCryptsHud.getValue();
        }
    }

    /** 死亡次数：Death: 0。 */
    public static class DeathsHUD extends DungeonHUD {

        @Override
        protected Identifier hudId() {
            return Identifier.fromNamespaceAndPath("skydiao", "dungeon_deaths");
        }

        @Override
        protected String hudText() {
            return "§cDeath: §f" + DungeonScoreListener.getDeaths();
        }

        @Override
        protected int row() {
            return 1;
        }

        @Override
        protected boolean visible() {
            return super.visible() && ConfigManager.dungeonDeathsHud.getValue();
        }
    }

    /** 当前房间的秘密进度：Secret: 2/5。 */
    public static class SecretsHUD extends DungeonHUD {

        @Override
        protected Identifier hudId() {
            return Identifier.fromNamespaceAndPath("skydiao", "dungeon_secrets");
        }

        @Override
        protected String hudText() {
            int[] progress = currentRoomSecrets();
            if (progress == null) return "§bSecret: §7-/-";
            if (progress[1] > 0 && progress[0] >= progress[1]) {
                return "§bSecret: §a" + progress[0] + "/" + progress[1];
            }
            return "§bSecret: §f" + progress[0] + "§7/" + progress[1];
        }

        @Override
        protected int column() {
            return 1;
        }

        @Override
        protected int row() {
            return 1;
        }

        @Override
        protected boolean visible() {
            return super.visible() && ConfigManager.dungeonSecretsHud.getValue();
        }

        private static int[] currentRoomSecrets() {
            DungeonMapScanListener.Snapshot snapshot = DungeonMapScanListener.getCurrent();
            if (snapshot == null) return null;
            int[] mapCell = DungeonMapUtils.mapPosFromPhysical(snapshot.physicalEntrance(), snapshot.mapEntrance(),
                    snapshot.mapRoomSize(), snapshot.playerCell());
            for (DungeonMapScanListener.Snapshot.RoomView view : snapshot.rooms()) {
                for (int[] segment : view.segments()) {
                    if (segment[0] == mapCell[0] && segment[1] == mapCell[1]) {
                        return new int[]{view.found(), view.total()};
                    }
                }
            }
            return null;
        }
    }
}
