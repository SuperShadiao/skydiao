package pers.XiaoShadiao.skydiao.config;

import org.junit.jupiter.api.Test;
import pers.XiaoShadiao.skydiao.config.option.ActionConfigOption;
import pers.XiaoShadiao.skydiao.config.option.BooleanConfigOption;
import pers.XiaoShadiao.skydiao.config.option.ConfigGroupOption;
import pers.XiaoShadiao.skydiao.config.option.ConfigOption;
import pers.XiaoShadiao.skydiao.config.option.IntConfigOption;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigOptionTreeTest {
    @Test
    void searchDestinationsRetainTheEntireParentPath() {
        IntConfigOption leaf = new IntConfigOption("leaf", 1);
        IntConfigOption sibling = new IntConfigOption("sibling", 2);
        ConfigGroupOption inner = new ConfigGroupOption("inner", List.of(leaf));
        ConfigGroupOption outer = new ConfigGroupOption("outer", List.of(inner, sibling));

        List<ConfigOptionTree.Entry> entries = ConfigOptionTree.entries(List.of(outer, sibling));
        assertEquals(List.of(
                new ConfigOptionTree.Entry(outer, List.of()),
                new ConfigOptionTree.Entry(inner, List.of(outer)),
                new ConfigOptionTree.Entry(leaf, List.of(outer, inner)),
                new ConfigOptionTree.Entry(sibling, List.of(outer)),
                new ConfigOptionTree.Entry(sibling, List.of())), entries);
    }

    @Test
    void globalDisableReachesNestedSettings() {
        BooleanConfigOption master = new BooleanConfigOption("master", true);
        BooleanConfigOption child = new BooleanConfigOption("child", true);
        ConfigGroupOption inner = new ConfigGroupOption("inner", List.of(child));
        ConfigGroupOption outer = new ConfigGroupOption("outer", List.of(inner));

        ConfigOptionTree.disableAll(List.of(master, outer));

        // isDefaultValue reads the local value without initializing Minecraft's ConfigManager.
        assertFalse(master.isDefaultValue());
        assertFalse(child.isDefaultValue());
    }

    @Test
    void pageOperationsDoNotChangeTheMasterOrOtherPages() {
        BooleanConfigOption master = new BooleanConfigOption("master", true);
        BooleanConfigOption unrelated = new BooleanConfigOption("unrelated", true);
        BooleanConfigOption child = new BooleanConfigOption("child", true);
        IntConfigOption count = new IntConfigOption("count", 3);
        count.setValue(8);
        ConfigGroupOption group = new ConfigGroupOption("page", List.of(child, count));

        ConfigOptionTree.disableAll(group.getOptions());
        assertFalse(child.isDefaultValue());
        assertEquals(8, count.getValue());
        assertTrue(master.isDefaultValue());
        assertTrue(unrelated.isDefaultValue());

        ConfigOptionTree.resetAll(group.getOptions());
        assertTrue(child.isDefaultValue());
        assertEquals(3, count.getValue());
        assertTrue(master.isDefaultValue());
        assertTrue(unrelated.isDefaultValue());
    }

    @Test
    void resetReachesChildrenWithoutExecutingActionButtons() {
        AtomicInteger actions = new AtomicInteger();
        ActionConfigOption action = new ActionConfigOption("open", actions::incrementAndGet);
        IntConfigOption child = new IntConfigOption("child", 1);
        child.setValue(2);
        ConfigGroupOption group = new ConfigGroupOption("group", List.of(action, child));

        ConfigOptionTree.resetAll(List.of(action, group));

        assertEquals(1, child.getValue());
        assertEquals(0, actions.get());
    }

    @Test
    void sharedOptionsArePersistedAndResetOnlyOnce() {
        AtomicInteger resets = new AtomicInteger();
        IntConfigOption shared = new IntConfigOption("stable.config.key", 1) {
            @Override
            public void resetToDefault() {
                resets.incrementAndGet();
                super.resetToDefault();
            }
        };
        ConfigGroupOption group = new ConfigGroupOption("group", List.of(shared));
        List<ConfigOption<?>> options = List.of(shared, group);

        assertEquals(List.of(shared, group), ConfigOptionTree.flatten(options));
        shared.setValue(9);
        assertSame(shared, group.getOptions().getFirst());
        ConfigOptionTree.resetAll(options);
        assertEquals(1, resets.get());
        assertEquals("stable.config.key", shared.getName());
        assertEquals(1, shared.getValue());
    }
}
