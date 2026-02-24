package pers.XiaoShadiao.skydiao.config.option;

import java.util.List;

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
        return displayOptions.get(value);
    }

    public void switchOption() {
        value++;
        if (value >= displayOptions.size()) value = 0;
    }

}
