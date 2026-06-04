package pers.XiaoShadiao.skydiao.utils.blivesensitiveword;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

import java.util.Optional;

public record ServerIdSpooferLiteralContents(String text) implements PlainTextContents {

    public ServerIdSpooferLiteralContents(PlainTextContents plainTextContents) {
        this(plainTextContents.text());
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
        return contentConsumer.accept(this.getReplaced());
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
        return styledContentConsumer.accept(style, this.getReplaced());
    }

    public String toString() {
        return "literal{" + this.text + "}";
    }

    @Override
    public @NotNull String text() {
        return getReplaced();
    }

    private String getReplaced() {
        if(StatusManager.get().isMegaServer()) {
            return text
                    .replace(String.valueOf(StatusManager.get().getServerID()), ServerIdSpoofer.getMegaServerId())
                    .replace(String.valueOf(StatusManager.get().getSmallServerID()), ServerIdSpoofer.getSmallMegaServerId());
        } else if (StatusManager.get().isMiniServer()) {
            return text
                    .replace(String.valueOf(StatusManager.get().getServerID()), ServerIdSpoofer.getMiniServerId())
                    .replace(String.valueOf(StatusManager.get().getSmallServerID()), ServerIdSpoofer.getSmallMiniServerId());
        }
        return text;
    }
}

