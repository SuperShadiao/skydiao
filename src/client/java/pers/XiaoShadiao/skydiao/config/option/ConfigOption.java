package pers.XiaoShadiao.skydiao.config.option;

import java.util.ArrayList;
import java.util.List;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class ConfigOption<T, S extends ConfigOption<T, S>> {

    private boolean isMacroFeature;
    protected ModDepends requiredMod;
    protected boolean isRequiredModInstalled;

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

    public void resetToDefault() {
        setValue(getDefaultValue());
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

    public boolean isMacroFeature() {
        return isMacroFeature;
    }

    public ModDepends getRequiredMod() {
        return requiredMod;
    }

    public S flagAsMacroFeature() {
        isMacroFeature = true;
        return cast(this);
    }

    public S setRequiredMod(ModDepends requiredMod) {
        this.requiredMod = requiredMod;
        return cast(this);
    }

    @SuppressWarnings("unchecked")
    private S cast(ConfigOption<T, S> i) {
        return (S) i;
    }

    public boolean isRequiredModInstalled() {
        return isRequiredModInstalled;
    }

    public void setRequiredModInstalled() {
        isRequiredModInstalled = true;
    }

    public record ModDepends(String modId, String requiredVersion, String downloadUrl) { }
}