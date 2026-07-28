package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;

public class HudConfigScreen extends Screen {

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    private final Screen parent;
    private XSDHUD currentSelected;

    public HudConfigScreen(Screen parent) {
        super(Component.literal("编辑Hud (左键拖动改变位置, 右键拖动改变大小)"));
        this.parent = parent;
    }

    @Override
    protected void init() {

        layout.addTitleHeader(title, font);
        layout.arrangeElements();
        LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(Button.builder(Component.literal("点击选择HUD"), (b) -> {
            do {
                currentSelected = XSDHUD.huds.get((XSDHUD.huds.indexOf(currentSelected) + 1) % XSDHUD.huds.size());
            } while (currentSelected.getHudName() == null);
            b.setMessage(Component.literal("当前选择: " + currentSelected.getHudName()));
        }).size(100, 20).build());
        linearLayout.addChild(Button.builder(Component.literal("返回"), (b) -> minecraft.setScreen(parent)).size(50, 20).build());
        linearLayout.addChild(Button.builder(Component.literal("重置"), (b) -> XSDHUD.resetHudSetting(currentSelected)).size(50, 20).build());
        this.layout.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });
        this.repositionElements();

    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        super.extractRenderState(guiGraphics, i, j, f);
        if(currentSelected != null) {
            currentSelected.extractRenderState(guiGraphics, DeltaTracker.ZERO, true);
        }
    }

    @Override
    public void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if(super.mouseDragged(event, dx, dy)) return true;
        if(currentSelected != null) {
            if(event.button() == 0) {
                XSDHUD.saveHudAppendOffset(currentSelected, (float) dx, (float) dy);
                return true;
            } else if(event.button() == 1) {
                XSDHUD.saveHudAppendScale(currentSelected, (float) (dx + dy) / 1000);
                return true;
            }
        }
        return false;
    }
}
