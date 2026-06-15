package pers.XiaoShadiao.skydiao.screen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.minecraft.CrashReport;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinecraftCrashedScreen extends Screen {

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    private final Minecraft mc;
    private Object suspiciousListener, suspiciousMod;

    private final Throwable throwable;
    private final CrashReport crashReport;
    private final Path file;

    public MinecraftCrashedScreen(Throwable throwable, CrashReport crashReport) {
        super(Component.literal("Minecraft爆炸了"));
        this.mc = ToolList.mc;
        this.throwable = throwable;
        this.crashReport = crashReport;
        this.file = crashReport.getSaveFile();
    }

    @Override
    public void removed() {
        CrashReport.preload();
        ToolList.getInstance().log.info("已重新创建内存缓冲");
    }

    @Override
    protected void init() {

        layout.addTitleHeader(Component.literal("§e§k|§e Minecraft爆炸了 §e§k|"), font);

        this.addRenderableWidget(Button.builder(Component.literal("§b打开因爆炸生成的文件"), button -> {
            Util.getPlatform().openUri(file.toUri());
        }).bounds(10, this.height - 30, 200, 20).build()).active = file != null;

        this.addRenderableWidget(Button.builder(Component.literal("§a§kA§a 谢谢小沙雕! §a§kA"), button -> {
            mc.setScreen(new TitleScreen());
        }).bounds(this.width - 210, this.height - 30, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("强制关闭客户端"), button -> {
            if(ToolList.getInstance().isDevEnvironment()) throw new UnsupportedOperationException(); else mc.stop();
        }).bounds(this.width / 2 - 100, this.height - 60, 200, 20).build());
        ModInfo info = getSuspiciousInfo();

        suspiciousListener = info.className == null ? "Minecraft" : info.className;
        suspiciousMod = info.mod == null ? "Minecraft" : info.mod.getProvider().getMetadata().getName();

        this.layout.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });
        layout.arrangeElements();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.text(this.font, "§eMinecraft因为某个原因爆炸了", 25, 80, 0xFFFFFFFF, false);
        context.text(this.font, "§e可能引起爆炸的Class类: §c" + suspiciousListener, 25, 80 + this.font.lineHeight * 1, 0xFFFFFFFF, false);
        context.text(this.font, "§e可能引起爆炸的MOD: §c" + suspiciousMod, 25, 80 + this.font.lineHeight * 2, 0xFFFFFFFF, false);

        context.text(this.font, "§a但是, 小沙雕使用了他存储的§e114514§a天的体力", 25, 80 + this.font.lineHeight * 5, 0xFFFFFFFF, false);
        context.text(this.font, "§a并释放出一种极其强大的§6Homo§a之力, 保护了Minecraft没有因爆炸而完全损坏", 25, 80 + this.font.lineHeight * 6, 0xFFFFFFFF, false);
        context.text(this.font, "§d** 哼--哼-- 啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊... **", 25, 80 + this.font.lineHeight * 7, 0xFFFFFFFF, false);
        context.text(this.font, "§a还不快感谢小沙雕?", 25, 80 + this.font.lineHeight * 9, 0xFFFFFFFF, false);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    public record ModInfo(EntrypointContainer<?> mod, String stack, String className) { }

    public ModInfo getSuspiciousInfo() {
        List<EntrypointContainer<?>> list = new ArrayList<>();
        list.addAll(FabricLoaderImpl.INSTANCE.getEntrypointContainers("main", ModInitializer.class));
        list.addAll(FabricLoaderImpl.INSTANCE.getEntrypointContainers("client", ClientModInitializer.class));

        int index = Integer.MAX_VALUE;
        EntrypointContainer<?> currentClassName = null;
        StackTraceElement[] stacks = throwable.getStackTrace();
        String currentStack = null;
        String currentStackClassName = null;
        for (EntrypointContainer<?> container : list) {
            String entryPoint = container.getDefinition();
            Matcher matcher = Pattern.compile(".*?\\..*?\\..*?\\.").matcher(entryPoint);
            String packageName = matcher.find() ? matcher.group() : null;
            if(packageName != null) {
                for (int i = 0; i < stacks.length; i++) {
                    StackTraceElement stack = stacks[i];
                    if(stack.getClassName().startsWith(packageName)) {
                        if(i < index) {
                            index = i;
                            currentClassName = container;
                            currentStack = stack.toString();
                            currentStackClassName = stack.getClassName();
                        }
                    }
                }
            }
        }

        return new ModInfo(currentClassName, currentStack, currentStackClassName);
    }
}
