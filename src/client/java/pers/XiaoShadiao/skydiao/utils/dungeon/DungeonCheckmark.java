package pers.XiaoShadiao.skydiao.utils.dungeon;

/** 房间在地图上的勾叉状态。 */
public enum DungeonCheckmark {

    /** 没有勾叉（未通关，或该房间类型不画）。 */
    NONE((byte) -1),
    /** 白勾：已通关，但秘密没找全。 */
    WHITE(DungeonMapUtils.COLOR_WHITE_CHECK),
    /** 绿勾：已通关且秘密全部找到。 */
    GREEN(DungeonMapUtils.COLOR_GREEN_CHECK),
    /** 红叉：解密失败。 */
    RED(DungeonMapUtils.COLOR_RED_CROSS);

    public final byte color;

    DungeonCheckmark(byte color) {
        this.color = color;
    }

    public static DungeonCheckmark byColor(byte color) {
        for (DungeonCheckmark checkmark : values()) {
            if (checkmark != NONE && checkmark.color == color) return checkmark;
        }
        return NONE;
    }
}
