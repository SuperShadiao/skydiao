package pers.XiaoShadiao.skydiao.utils.calc;

public interface IStack<T extends Stackable> {

    public T pop(boolean remove);
    public void push(T stackable);

}
