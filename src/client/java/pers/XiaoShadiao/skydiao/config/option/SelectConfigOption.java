package pers.XiaoShadiao.skydiao.config.option;

import net.minecraft.util.Mth;

import java.util.List;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class SelectConfigOption extends IntConfigOption {

    private final List<String> displayOptions;

    public SelectConfigOption(String name, int defaultValue, List<String> displayOptions) {
        super(name, defaultValue);
        this.displayOptions = displayOptions;
    }

    @Override
    public Integer getValue() {
        if (value < 0 || value >= displayOptions.size()) value = 0;
        return super.getValue();
    }

    public String getCurrentDisplayString() {
        if (value < 0 || value >= displayOptions.size()) value = 0;
        return translate(displayOptions.get(value));
    }

    @Override
    public String getI18nValue() {
        return getCurrentDisplayString();
    }

    public void switchOption() {
        value++;
        if (value >= displayOptions.size()) value = 0;
    }

    @Override
    public void setValue(Integer value) {
        super.setValue(Mth.clamp(value, 0, displayOptions.size() - 1));
    }
}
