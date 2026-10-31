package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class LegacyChat {

    private LegacyChat() {
    }

    public static boolean hasLegacyCodes(Component component) {
        return component != null && component.getString().indexOf('\u00a7') >= 0;
    }

    public static Component parse(Component component) {
        if (!hasLegacyCodes(component)) return component;
        MutableComponent parsed = parse(component.getString());
        parsed.setStyle(component.getStyle());
        return parsed;
    }

    public static MutableComponent parse(String text) {
        MutableComponent result = Component.empty();
        StringBuilder buffer = new StringBuilder();
        Style style = Style.EMPTY;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                Style next = apply(style, Character.toLowerCase(text.charAt(i + 1)));
                if (next != null) {
                    if (buffer.length() > 0) {
                        result.append(Component.literal(buffer.toString()).setStyle(style));
                        buffer.setLength(0);
                    }
                    style = next;
                    i++;
                    continue;
                }
            }
            buffer.append(c);
        }
        if (buffer.length() > 0) {
            result.append(Component.literal(buffer.toString()).setStyle(style));
        }
        return result;
    }

    private static Style apply(Style style, char code) {
        return switch (code) {
            case '0' -> Style.EMPTY.withColor(0x000000);
            case '1' -> Style.EMPTY.withColor(0x0000AA);
            case '2' -> Style.EMPTY.withColor(0x00AA00);
            case '3' -> Style.EMPTY.withColor(0x00AAAA);
            case '4' -> Style.EMPTY.withColor(0xAA0000);
            case '5' -> Style.EMPTY.withColor(0xAA00AA);
            case '6' -> Style.EMPTY.withColor(0xFFAA00);
            case '7' -> Style.EMPTY.withColor(0xAAAAAA);
            case '8' -> Style.EMPTY.withColor(0x555555);
            case '9' -> Style.EMPTY.withColor(0x5555FF);
            case 'a' -> Style.EMPTY.withColor(0x55FF55);
            case 'b' -> Style.EMPTY.withColor(0x55FFFF);
            case 'c' -> Style.EMPTY.withColor(0xFF5555);
            case 'd' -> Style.EMPTY.withColor(0xFF55FF);
            case 'e' -> Style.EMPTY.withColor(0xFFFF55);
            case 'f' -> Style.EMPTY.withColor(0xFFFFFF);
            case 'l' -> style.withBold(true);
            case 'o' -> style.withItalic(true);
            case 'n' -> style.withUnderlined(true);
            case 'm' -> style.withStrikethrough(true);
            case 'k' -> style.withObfuscated(true);
            case 'r' -> Style.EMPTY;
            default -> null;
        };
    }
}
