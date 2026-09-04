package pers.XiaoShadiao.skydiao.utils.blivesensitiveword;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

public class ComponentHelper {

    public static Component wrapAsSensitive(Component component, boolean scanEnglish, boolean scanChinese) {
        MutableComponent component1;
        if(component.getContents() instanceof PlainTextContents plainTextContents) {
            component1 = MutableComponent.create(new SensitiveWordLiteralContents(plainTextContents, scanEnglish, scanChinese));
        } else if (component instanceof MutableComponent mutableComponent) {
            return mutableComponent;
        } else {
            component1 = MutableComponent.create(component.getContents());
        }
        component1.setStyle(component.getStyle());
        component.getSiblings().forEach(component2 -> component1.append(wrapAsSensitive(component2, scanEnglish, scanChinese)));
        return component1;
    }

    public static Component unwrapSensitive(Component component) {
        MutableComponent component1;
        if(component.getContents() instanceof SensitiveWordLiteralContents plainTextContents) {
            component1 = MutableComponent.create(new PlainTextContents.LiteralContents(plainTextContents.getOriginalText()));
        } else {
            component1 = MutableComponent.create(component.getContents());
        }
        component1.setStyle(component.getStyle());
        component.getSiblings().forEach(component2 -> component1.append(unwrapSensitive(component2)));
        return component1;
    }

    public static Component wrapAsServerIdSpoof(Component component) {
        MutableComponent component1;
        if(component.getContents() instanceof PlainTextContents plainTextContents) {
            component1 = MutableComponent.create(new ServerIdSpooferLiteralContents(plainTextContents));
        } else if (component instanceof MutableComponent mutableComponent) {
            return mutableComponent;
        } else {
            component1 = MutableComponent.create(component.getContents());
        }
        component1.setStyle(component.getStyle());
        component.getSiblings().forEach(component2 -> component1.append(wrapAsServerIdSpoof(component2)));
        return component1;
    }

}
