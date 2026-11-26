package pers.XiaoShadiao.skydiao.utils.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.Map;

/**
 * 将 Hypixel / NEU 的 legacy 文本（§ 颜色码）转换为 Component。
 */
public final class LegacyText {

    private static final Map<Character, ChatFormatting> CODES = new HashMap<>();

    static {
        CODES.put('0', ChatFormatting.BLACK);
        CODES.put('1', ChatFormatting.DARK_BLUE);
        CODES.put('2', ChatFormatting.DARK_GREEN);
        CODES.put('3', ChatFormatting.DARK_AQUA);
        CODES.put('4', ChatFormatting.DARK_RED);
        CODES.put('5', ChatFormatting.DARK_PURPLE);
        CODES.put('6', ChatFormatting.GOLD);
        CODES.put('7', ChatFormatting.GRAY);
        CODES.put('8', ChatFormatting.DARK_GRAY);
        CODES.put('9', ChatFormatting.BLUE);
        CODES.put('a', ChatFormatting.GREEN);
        CODES.put('b', ChatFormatting.AQUA);
        CODES.put('c', ChatFormatting.RED);
        CODES.put('d', ChatFormatting.LIGHT_PURPLE);
        CODES.put('e', ChatFormatting.YELLOW);
        CODES.put('f', ChatFormatting.WHITE);
        CODES.put('k', ChatFormatting.OBFUSCATED);
        CODES.put('l', ChatFormatting.BOLD);
        CODES.put('m', ChatFormatting.STRIKETHROUGH);
        CODES.put('n', ChatFormatting.UNDERLINE);
        CODES.put('o', ChatFormatting.ITALIC);
        CODES.put('r', ChatFormatting.RESET);
    }

    private LegacyText() {
    }

    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        MutableComponent result = Component.empty();
        Style style = Style.EMPTY.withItalic(false);
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current != '§' || i + 1 >= text.length()) {
                continue;
            }

            char code = Character.toLowerCase(text.charAt(i + 1));
            ChatFormatting formatting = CODES.get(code);
            if (formatting == null) {
                continue;
            }

            if (i > start) {
                append(result, text.substring(start, i), style);
            }
            style = apply(style, code, formatting);
            i++;
            start = i + 1;
        }

        if (start < text.length()) {
            append(result, text.substring(start), style);
        }
        return result;
    }

    private static Style apply(Style style, char code, ChatFormatting formatting) {
        if (code == 'r') {
            return Style.EMPTY.withItalic(false);
        }
        if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f') {
            return Style.EMPTY.withItalic(false).withColor(formatting);
        }
        return switch (code) {
            case 'l' -> style.withBold(true);
            case 'm' -> style.withStrikethrough(true);
            case 'n' -> style.withUnderlined(true);
            case 'o' -> style.withItalic(true);
            case 'k' -> style.withObfuscated(true);
            default -> style;
        };
    }

    private static void append(MutableComponent result, String text, Style style) {
        if (!text.isEmpty()) {
            result.append(Component.literal(text).withStyle(style));
        }
    }
}
