package pers.XiaoShadiao.skydiao.utils.calc;

public record Number(java.lang.Number value) implements Stackable {

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
