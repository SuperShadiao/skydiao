package pers.XiaoShadiao.skydiao.config.option;

public class ActionConfigOption extends ConfigOption<Object> {

    private final Runnable action;
    public ActionConfigOption(String name, Runnable action) {
        super(name, null);
        this.action = action;
    }

    @Override
    public void setValue(Object anything) {
        action.run();
    }

    @Override
    public Object getValue() {
        throw new UnsupportedOperationException("getValue is not supported");
    }

    @Override
    public String getI18nValue() {
        return "->";
    }

}
