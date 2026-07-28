package pers.XiaoShadiao.skydiao.utils.screen;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class XSDSliderButton extends AbstractSliderButton {

    public DoubleSupplier valueGetter = () -> 0;
    public Supplier<Component> messageGetter = Component::empty;
    public DoubleConsumer valueSetter = _ -> {};

    public XSDSliderButton(int x, int y, int width, int height, Component message, double initialValue) {
        super(x, y, width, height, message, initialValue);

        updateMessage();
        setValue(valueGetter.getAsDouble());
    }

    @Override
    protected void updateMessage() {
        setMessage(messageGetter.get());
    }

    @Override
    protected void applyValue() {
        valueSetter.accept(value);
    }

    public XSDSliderButton valueGetter(DoubleSupplier getter) {
        valueGetter = getter;
        setValue(valueGetter.getAsDouble());
        return this;
    }

    public XSDSliderButton stringMsgGetter(Supplier<String> getter) {
        messageGetter = () -> Component.literal(getter.get());
        updateMessage();
        return this;
    }

    public XSDSliderButton componentMsgGetter(Supplier<Component> getter) {
        messageGetter = getter;
        return this;
    }

    public XSDSliderButton valueSetter(DoubleConsumer setter) {
        valueSetter = setter;
        return this;
    }

    public double getValue() {
        return value;
    }

}
