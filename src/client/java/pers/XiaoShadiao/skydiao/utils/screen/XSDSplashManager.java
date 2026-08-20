package pers.XiaoShadiao.skydiao.utils.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SpecialDates;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.XSDSafeSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.MonthDay;
import java.util.List;
import java.util.Locale;

public class XSDSplashManager extends SplashManager {

    private static final Style DEFAULT_STYLE = Style.EMPTY.withColor(-256);

    private XSDSafeSession user;

    private static Component literalSplash(final String text) {
        return Component.literal(text).setStyle(DEFAULT_STYLE);
    }

    public XSDSplashManager(XSDSafeSession user) {
        super(user);
        this.user = user;
    }

    @Override
    public @Nullable SplashRenderer getSplash() {
        int birthday = ToolList.getInstance().getXiaoShadiaoBirthdayRemain();
        if(birthday != Integer.MAX_VALUE) {
            return new SplashRenderer(literalSplash(birthday == 0 ? "小沙雕, 生日快乐!" : "你知道吗, " + birthday + "天后就是小沙雕生日了!"));
        } else {
            int i = ToolList.getInstance().getGaokaoRemainDays();
            if(!(i > 30 && ToolList.getInstance().random.nextInt(10) != 5)) {
                if (i > 0) {
                    return new SplashRenderer(literalSplash("距离小沙雕の高考倒计时还剩" + i + "天! qwq"));
                } else if (i > -3) {
                    return new SplashRenderer(literalSplash("小沙雕! §a高考加油! (ง •̀_•́)ง"));
                }
            }
        }
        return getDefaultSplash();
    }

    public void setUser(XSDSafeSession user) {
        this.user = user;
    }


    // 原版代码

    public static final Component CHRISTMAS = literalSplash("Merry X-mas!");
    public static final Component NEW_YEAR = literalSplash("Happy new year!");
    public static final Component HALLOWEEN = literalSplash("OOoooOOOoooo! Spooky!");
    private static final Identifier SPLASHES_LOCATION = Identifier.withDefaultNamespace("texts/splashes.txt");
    private static final RandomSource RANDOM = RandomSource.create();
    private List<Component> splashes = List.of();

    @SuppressWarnings("all")
    protected List<Component> prepare(final ResourceManager manager, final ProfilerFiller profiler) {
        try {
            BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(SPLASHES_LOCATION);

            List var4;
            try {
                var4 = reader.lines().map(String::trim).filter(line -> line.hashCode() != 125780783).map(XSDSplashManager::literalSplash).toList();
            } catch (Throwable var7) {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Throwable var6) {
                        var7.addSuppressed(var6);
                    }
                }

                throw var7;
            }

            if (reader != null) {
                reader.close();
            }

            return var4;
        } catch (IOException var8) {
            return List.of();
        }
    }

    @SuppressWarnings("all")
    protected void apply(final List<Component> preparations, final ResourceManager manager, final ProfilerFiller profiler) {
        this.splashes = List.copyOf(preparations);
    }

    private @Nullable SplashRenderer getDefaultSplash() {
        MonthDay monthDay = SpecialDates.dayNow();
        if (monthDay.equals(SpecialDates.CHRISTMAS)) {
            return SplashRenderer.CHRISTMAS;
        } else if (monthDay.equals(SpecialDates.NEW_YEAR)) {
            return SplashRenderer.NEW_YEAR;
        } else if (monthDay.equals(SpecialDates.HALLOWEEN)) {
            return SplashRenderer.HALLOWEEN;
        } else if (this.splashes.isEmpty()) {
            return null;
        } else {
            return this.user != null && RANDOM.nextInt(this.splashes.size()) == 42
                    ? new SplashRenderer(literalSplash(this.user.getName().toUpperCase(Locale.ROOT) + " IS YOU"))
                    : new SplashRenderer(this.splashes.get(RANDOM.nextInt(this.splashes.size())));
        }
    }

}
