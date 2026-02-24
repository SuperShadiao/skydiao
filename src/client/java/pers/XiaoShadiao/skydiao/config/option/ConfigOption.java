package pers.XiaoShadiao.skydiao.config.option;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class ConfigOption<T> {

    protected final String name;
    protected final T defaultValue;
    protected T value;

    public ConfigOption(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public void reset() {
        this.value = defaultValue;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String toString() {
        return "ConfigOption{" +
                "name='" + name + '\'' +
                ", defaultValue=" + defaultValue +
                ", value=" + value +
                '}';
    }

    public String getI18nName() {
        return translate("config." + name + ".configname");
    }

    public String getI18nDesc() {
        return translate("config." + name + ".description");
    }

    public String getI18nValue() {
        return value.toString();
    }

}
