package pers.XiaoShadiao.skydiao.utils.crystalhollows;

/** Scan targets shared by the settings and scanner registry. Config IDs must remain stable. */
public enum StructureType {
    BLUE("blue", "§b蓝色水晶", "Lost Precursor City", 121, 130),
    PURPLE("purple", "§5紫色水晶", "Jungle Temple", 72, 81),
    YELLOW("yellow", "§e黄色水晶", "Khazad-dûm", 0, 63),
    ORANGE("orange", "§6橙色水晶", "Goblin Queen's Den", 125, 140),
    GREEN("green", "§a绿色水晶", "Mines of Divan", 97, 102),
    GOBLIN_KING("goblin_king", "§6王下一桶", "King Yolkar", 82, 168),
    DRAGON_LAIR("dragon_lair", "§c那位来客", "Dragon's Lair", 64, 189),
    WORM_FISH_SPOT("worm_fish_spot", "§c可以烤鱼钩的地方", "Unknown", 64, 189),
    CORLEONE("corleone", "§a骷髅王", "Corleone", 64, 189),
    FAIRY_GROTTO("fairy_grotto", "§d粉色小狗", "Fairy Grotto", 64, 189),
    BEAR3("bear3", "§e熊出没", "Unknown", 64, 189);

    private final String configName;
    private final String displayName;
    private final String waypointName;
    private final int minY;
    private final int maxY;

    StructureType(String id, String displayName, String waypointName, int minY, int maxY) {
        this.configName = "crystalhollows.scan." + id;
        this.displayName = displayName;
        this.waypointName = waypointName;
        this.minY = minY;
        this.maxY = maxY;
    }

    public String configName() {
        return configName;
    }

    public String displayName() {
        return displayName;
    }

    public String waypointName() {
        return waypointName;
    }

    public boolean isInRange(int y) {
        return y >= minY && y <= maxY;
    }
}
