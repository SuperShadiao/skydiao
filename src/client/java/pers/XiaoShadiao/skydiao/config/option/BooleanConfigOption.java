package pers.XiaoShadiao.skydiao.config.option;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class BooleanConfigOption extends ConfigOption<Boolean> {
    public BooleanConfigOption(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    @Override
    public String getI18nValue() {
        return translate(value ? "config.1" : "config.0");
    }
}
