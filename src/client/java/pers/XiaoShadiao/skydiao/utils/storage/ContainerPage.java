package pers.XiaoShadiao.skydiao.utils.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个可预览的存储页：末影箱 1~9 或背包 1~18。
 */
public record ContainerPage(Kind kind, int index) {

    public enum Kind {
        ENDER_CHEST("ec", "末影箱"),
        BACKPACK("bp", "背包");

        private final String keyPrefix;
        private final String displayPrefix;

        Kind(String keyPrefix, String displayPrefix) {
            this.keyPrefix = keyPrefix;
            this.displayPrefix = displayPrefix;
        }
    }

    public String key() {
        return kind.keyPrefix + ":" + index;
    }

    public String displayName() {
        return kind.displayPrefix + " #" + index;
    }

    public String command() {
        return kind == Kind.ENDER_CHEST ? "/enderchest " + index : "/backpack " + index;
    }

    public static ContainerPage enderChest(int index) {
        return new ContainerPage(Kind.ENDER_CHEST, index);
    }

    public static ContainerPage backpack(int index) {
        return new ContainerPage(Kind.BACKPACK, index);
    }

    public static List<ContainerPage> all() {
        List<ContainerPage> pages = new ArrayList<>(27);
        for (int i = 1; i <= 9; i++) {
            pages.add(enderChest(i));
        }
        for (int i = 1; i <= 18; i++) {
            pages.add(backpack(i));
        }
        return List.copyOf(pages);
    }
}
