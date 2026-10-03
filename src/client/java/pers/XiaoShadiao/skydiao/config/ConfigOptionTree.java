package pers.XiaoShadiao.skydiao.config;

import pers.XiaoShadiao.skydiao.config.option.ActionConfigOption;
import pers.XiaoShadiao.skydiao.config.option.BooleanConfigOption;
import pers.XiaoShadiao.skydiao.config.option.ConfigGroupOption;
import pers.XiaoShadiao.skydiao.config.option.ConfigOption;

import java.util.ArrayList;
import java.util.List;

/** Shared traversal for settings navigation, persistence and scoped bulk operations. */
public final class ConfigOptionTree {
    private ConfigOptionTree() { }

    public record Entry(ConfigOption<?> option, List<ConfigGroupOption> parents) {
        public Entry {
            parents = List.copyOf(parents);
        }
    }

    public static List<Entry> entries(List<ConfigOption<?>> options) {
        List<Entry> result = new ArrayList<>();
        visit(options, List.of(), result);
        return List.copyOf(result);
    }

    private static void visit(List<ConfigOption<?>> options, List<ConfigGroupOption> parents, List<Entry> result) {
        for (ConfigOption<?> option : options) {
            result.add(new Entry(option, parents));
            if (option instanceof ConfigGroupOption group) {
                List<ConfigGroupOption> path = new ArrayList<>(parents);
                path.add(group);
                visit(group.getOptions(), path, result);
            }
        }
    }

    public static List<ConfigOption<?>> flatten(List<ConfigOption<?>> options) {
        return entries(options).stream().map(Entry::option).distinct().toList();
    }

    public static void disableAll(List<ConfigOption<?>> options) {
        for (ConfigOption<?> option : flatten(options)) {
            if (option instanceof BooleanConfigOption bool) bool.setValue(false);
        }
    }

    public static void resetAll(List<ConfigOption<?>> options) {
        for (ConfigOption<?> option : flatten(options)) {
            // Navigation and action buttons have no stored value and must never execute during a reset.
            if (!(option instanceof ActionConfigOption) && !(option instanceof ConfigGroupOption)) {
                option.resetToDefault();
            }
        }
    }
}
