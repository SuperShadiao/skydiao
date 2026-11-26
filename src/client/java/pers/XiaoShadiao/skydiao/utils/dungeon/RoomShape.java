package pers.XiaoShadiao.skydiao.utils.dungeon;

/**
 * 房间形状分组，对应数据目录 {@code assets/skydiao/dungeons/<dungeon>/rooms/<folder>}。
 *
 * <p>普通房按占地格数分组；陷阱、解密、小 Boss 是特殊类型，各自一个目录。
 * 少数形状（L 形、特殊房）的格数不固定，由模板方块的实际范围推出，所以这里填 0。
 */
public enum RoomShape {

    ONE_BY_ONE("1x1", 1, 1),
    ONE_BY_TWO("1x2", 1, 2),
    ONE_BY_THREE("1x3", 1, 3),
    ONE_BY_FOUR("1x4", 1, 4),
    TWO_BY_TWO("2x2", 2, 2),
    L_SHAPE("l-shape", 0, 0),
    MINIBOSS("miniboss", 0, 0),
    PUZZLE("puzzle", 0, 0),
    TRAP("trap", 0, 0);

    private final String folder;
    private final int cellsX;
    private final int cellsZ;

    RoomShape(String folder, int cellsX, int cellsZ) {
        this.folder = folder;
        this.cellsX = cellsX;
        this.cellsZ = cellsZ;
    }

    public String folder() {
        return folder;
    }

    /** 模板占地的 x 方向格数；0 表示需要从模板方块推算。 */
    public int cellsX() {
        return cellsX;
    }

    /** 模板占地的 z 方向格数；0 表示需要从模板方块推算。 */
    public int cellsZ() {
        return cellsZ;
    }

    public static RoomShape byFolder(String folder) {
        for (RoomShape shape : values()) {
            if (shape.folder.equals(folder)) return shape;
        }
        return null;
    }
}
