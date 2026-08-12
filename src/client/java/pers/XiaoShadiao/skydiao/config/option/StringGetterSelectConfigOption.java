package pers.XiaoShadiao.skydiao.config.option;

import java.util.List;
import java.util.function.Supplier;

//可以使用supplier提供选项
public class StringGetterSelectConfigOption extends StringConfigOption {

    private final Supplier<List<String>> supplier;

    public StringGetterSelectConfigOption(String name, String defaultValue, Supplier<List<String>> supplier) {
        super(name, defaultValue);
        this.supplier = supplier;
    }

    public List<String> getValues() {
        return supplier.get();
    }





}
