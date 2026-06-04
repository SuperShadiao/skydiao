package pers.XiaoShadiao.skydiao.utils.blivesensitiveword;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class SensitiveWordLiteralContents implements PlainTextContents {

    public final String text;
    public final String sensitiveWord;

    public SensitiveWordLiteralContents(PlainTextContents plainTextContents, boolean scanEnglish, boolean scanChinese) {
        this.text = plainTextContents.text();
        this.sensitiveWord = IDetectorAccessor.getInstance().scanIllegalWords(this.text, scanEnglish, scanChinese).transfered;
    }

    public SensitiveWordLiteralContents(String text, boolean scanEnglish, boolean scanChinese) {
        this.text = text;
        this.sensitiveWord = IDetectorAccessor.getInstance().scanIllegalWords(text, scanEnglish, scanChinese).transfered;
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
        return contentConsumer.accept(this.sensitiveWord);
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
        return styledContentConsumer.accept(style, this.sensitiveWord);
    }

    public String toString() {
        return "literal{" + this.text + "}";
    }

    @Override
    public @NotNull String text() {
        return sensitiveWord;
    }
}

