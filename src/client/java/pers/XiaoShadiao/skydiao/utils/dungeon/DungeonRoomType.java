package pers.XiaoShadiao.skydiao.utils.dungeon;

/**
 * 地图上房间色块对应的房间类型。
 *
 * <p>颜色值来自 {@code MapItemSavedData#colors} 的打包值。注意有两个颜色是复用的：
 * 绿色 30 既是入口也是“绿勾”，红色 18 既是血门也是“红叉”，
 * 所以判断勾叉时不能只看颜色，必须看位置。
 */
public enum DungeonRoomType {

    ENTRANCE(DungeonMapUtils.COLOR_ENTRANCE),
    ROOM(DungeonMapUtils.COLOR_ROOM),
    TRAP(DungeonMapUtils.COLOR_TRAP),
    PUZZLE(DungeonMapUtils.COLOR_PUZZLE),
    MINIBOSS(DungeonMapUtils.COLOR_MINIBOSS),
    FAIRY(DungeonMapUtils.COLOR_FAIRY),
    BLOOD(DungeonMapUtils.COLOR_BLOOD),
    UNKNOWN(DungeonMapUtils.COLOR_UNKNOWN);

    public final byte color;

    DungeonRoomType(byte color) {
        this.color = color;
    }

    /** 是否有对应的房间模板数据（入口/仙女/血门是固定房间，不需要识别）。 */
    public boolean hasTemplate() {
        return this == ROOM || this == PUZZLE || this == TRAP || this == MINIBOSS;
    }

    /** 找不到匹配颜色时返回 {@code null}。 */
    public static DungeonRoomType byColor(byte color) {
        for (DungeonRoomType type : values()) {
            if (type.color == color) return type;
        }
        return null;
    }
}
