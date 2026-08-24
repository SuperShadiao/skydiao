package pers.XiaoShadiao.skydiao.utils.calc;

import java.util.ArrayList;
import java.util.List;

public class Stack<T extends Stackable> implements IStack<T> {
    public List<T> stacks = new ArrayList<>();

    @Override
    public T pop(boolean remove) {
        if (stacks.isEmpty()) return null;
        T s = stacks.getFirst();
        if (remove) stacks.removeFirst();
        return s;
    }

    @Override
    public void push(T stackable) {
        stacks.addFirst(stackable);
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public int size() {
        return stacks.size();
    }

    public T peek() {
        if (isEmpty()) return null;
        return stacks.getFirst();
    }

    public T pop() {
        if (isEmpty()) return null;
        return stacks.removeFirst();
    }
}