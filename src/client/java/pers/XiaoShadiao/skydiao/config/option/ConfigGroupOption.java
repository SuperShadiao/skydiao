package pers.XiaoShadiao.skydiao.config.option;

import java.util.List;

/** A navigation entry whose children remain ordinary, independently persisted options. */
public final class ConfigGroupOption extends ConfigOption<Void> {
    private final List<ConfigOption<?>> options;

    public ConfigGroupOption(String name, List<ConfigOption<?>> options) {
        super(name, null);
        this.options = List.copyOf(options);
    }

    public List<ConfigOption<?>> getOptions() {
        return options;
    }

    @Override
    public String getI18nValue() {
        return "->";
    }
}
