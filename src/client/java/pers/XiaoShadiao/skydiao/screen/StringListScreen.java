package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;
import pers.XiaoShadiao.skydiao.config.option.StringGetterSelectConfigOption;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;

import java.util.List;
import java.util.function.Supplier;

public class StringListScreen extends Screen {

    private static Minecraft mc = Minecraft.getInstance();
    private String the_title;
    private String subtitle;
    private String target;
    private boolean isPressedYet = false;
    private List<String> stringList;
    private StringGetterSelectConfigOption stringGetterSelectConfigOption;
    private int cy;

    public StringListScreen(String title, String subtitle, StringGetterSelectConfigOption stringGetterSelectConfigOption){
        this.the_title = title;
        this.subtitle = subtitle;
        this.stringList = stringGetterSelectConfigOption.getValues();
        this.stringGetterSelectConfigOption = stringGetterSelectConfigOption;
        super(Component.literal(title));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {

        int scaledWidth = mc.getWindow().getGuiScaledWidth();
        int scaledHeight = mc.getWindow().getGuiScaledHeight();
        int x1 = scaledWidth /8;
        int y1 = scaledHeight /6;
        int x2 = scaledWidth*7 /8;
        int y2 = scaledHeight*5 /6;

        if(isInSquare((int) event.x(), (int) event.y(),x1-2,y1-2,x2+2,y2+2))
        for(int i = 0; i < stringList.size(); i++){
            if(isInSquare((int) event.x(), (int) event.y(),x1,y1+i*16+cy,x2,y1+(i+1)*16+cy)) {
                target = stringList.get(i);
                subtitle = CrowdinI18nManager.translate("stringlistscreen.subtitle") +target;
            }
        }

        if(isInSquare((int) event.x(), (int) event.y(),x1,y2+6,x1+40,y2+26)&&target!=null){
            stringGetterSelectConfigOption.setValue(target);
            if(!isPressedYet){
                mc.player.sendSystemMessage(Component.literal(CrowdinI18nManager.translate("stringlistscreen.msgconfirm")+target).withColor(0x6694fa));
            }
        }

        if(isInSquare((int) event.x(), (int) event.y(),x1+50,y2+6,x1+90,y2+26)){
            this.onClose();
            mc.setScreenAndShow(new ConfigScreen(mc.screen));
        }

        isPressedYet = true;
        return super.mouseClicked(event,bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        isPressedYet = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount)
    {

        int scaledWidth = mc.getWindow().getGuiScaledWidth();
        int scaledHeight = mc.getWindow().getGuiScaledHeight();
        int x1 = scaledWidth /8;
        int y1 = scaledHeight /6;
        int x2 = scaledWidth*7 /8;
        int y2 = scaledHeight*5 /6;

        final int precy = (int) (cy + verticalAmount*4);
        final int h = y2 - y1;
        if(precy+stringList.size()*16>h)cy=Math.min(precy,0);
        else cy = Math.min(h-stringList.size()*16,0);

        return  super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY,
                                   float partialTicks)
    {
        int scaledWidth = mc.getWindow().getGuiScaledWidth();
        int scaledHeight = mc.getWindow().getGuiScaledHeight();
        int x1 = scaledWidth /8;
        int y1 = scaledHeight /6;
        int x2 = scaledWidth*7 /8;
        int y2 = scaledHeight*5 /6;
        context.text(mc.font,the_title,x1,y1-30,0xffffffff);
        context.text(mc.font,subtitle,x1,y1-15,0xffffffff);
        context.fill(x1-3,y1-3,x2+3,y2+3,0x60ffffff);
        drawBorder2D(context,x1-3,y1-3,x2+3,y2+3,0xffffffff);
        context.enableScissor(x1,y1,x2,y2);
        for(int i=0;i<stringList.size();i++)
        {
            if(isInSquare(mouseX,mouseY,x1-2,y1-2,x2+2,y2+2)&&isInSquare(mouseX,mouseY,x1,y1+i*16+cy,x2,y1+(i+1)*16+cy))
                drawBorder2D(context,x1-2,y1+i*16+cy,x2+2,y1+(i+1)*16+cy,0xb087fa66);
            context.text(mc.font,stringList.get(i),x1,y1+i*16+cy+4,0xffffffff);
        }
        context.disableScissor();

        context.fill(x1,y2+6,x1+40,y2+26,0xffadadad);
        context.text(mc.font,CrowdinI18nManager.translate("stringlistscreen.buttonconfirm"),x1+5, y2+12,0xffffffff);
        context.fill(x1+50,y2+6,x1+90,y2+26,0xffadadad);
        context.text(mc.font,CrowdinI18nManager.translate("stringlistscreen.buttonback"),x1+55, y2+12,0xffffffff);


        super.extractRenderState(context, mouseX, mouseY, partialTicks);

    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY,
                                  float deltaTicks)
    {


    }

    @Override
    public void init(){

    }


    public static void drawBorder2D(GuiGraphicsExtractor context, float x1, float y1,
                                    float x2, float y2, int color)
    {
        int scale = 1;
        int x = (int)(x1 * scale);
        int y = (int)(y1 * scale);
        int w = (int)((x2 - x1) * scale);
        int h = (int)((y2 - y1) * scale);

        context.pose().pushMatrix();
        context.pose().scale(1F / scale);
        context.outline(x, y, w, h, color);
        context.pose().popMatrix();
    }

    public static boolean isInSquare(int x, int y,int x1,int y1,int x2,int y2) {
        if(x1<=x && x2>=x && y1<=y && y2>=y) return true;
        return false;
    }

}
