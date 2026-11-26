package pers.XiaoShadiao.skydiao.utils.dungeon;

import java.util.regex.Pattern;

/**
 * 地牢队伍职业。
 *
 * <p>{@link #TAB_PATTERN} 匹配地牢 tab 里的玩家行，抄的 Skyblocker：
 * {@code [415] 5i_XiaoShadiao ⸕ (Archer XLVI)}。Hypixel 会把这类行塞进名字形如 {@code !A-b}
 * 的假 tab 条目里，真正的 {@code PlayerInfo} 显示名反而是 null，所以只能从所有 tab 行里反查职业。
 */
public enum DungeonClass {

    UNKNOWN("Unknown", 0xFFAAAAAA),
    HEALER("Healer", 0xFF820DD1),
    MAGE("Mage", 0xFF36C6E3),
    BERSERK("Berserk", 0xFFFA5B16),
    ARCHER("Archer", 0xFFED240E),
    TANK("Tank", 0xFF138717);

    /** 地牢 tab 里的队伍条目。 */
    public static final Pattern TAB_PATTERN = Pattern.compile(
            "\\[(?<level>\\d+)] (?:\\[[A-Za-z]+] )?(?<name>[A-Za-z0-9_]+) (?:.+ )?\\((?<class>\\S+) ?(?<classLevel>[LXVI0]+)?\\)");

    private final String displayName;
    private final int color;

    DungeonClass(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    /** ARGB 颜色，未知职业是灰色。 */
    public int color() {
        return color;
    }

    /** 按 tab 行里的职业名解析，无法识别时返回 {@link #UNKNOWN}。 */
    public static DungeonClass from(String name) {
        for (DungeonClass dungeonClass : values()) {
            if (dungeonClass.displayName.equalsIgnoreCase(name)) return dungeonClass;
        }
        return UNKNOWN;
    }
}
