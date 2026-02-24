package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.jtracy.DiscontinuousFrame;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsAvailability;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.screens.OutOfMemoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.SingleTickProfiler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.irc.ChatClient;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.screen.MinecraftCrashedScreen;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Final
    @Shadow
    private static Logger LOGGER;
    @Final
    @Shadow
    private User user;
    @Final
    @Shadow
    public File gameDirectory;
    @Shadow
    private Supplier<CrashReport> delayedCrash;

    // =========== SHADOW END =============

    @Unique
    private User user0;
    @Unique
    private int exceptionCounter;

    @Inject(at = @At("HEAD"), method = "run")
    private void init(CallbackInfo info) {
        // This code is injected into the start of Minecraft.run()V
    }

    @Overwrite
    public User getUser() {
        if(user0 == null) user0 = user;
        return user0;
    }

    public void setUser(User user) {
        if (user.equals(this.user0)) return;
        user0 = user;
        MixinRealmStatusReset.setFuture(null);
        try { ChatClient.socket.close(); } catch (Exception ignored) {}
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"), method = "run")
    public void run(Minecraft instance, boolean bl) {
        try {
            this.runTick(bl);
            this.handleDelayedCrash();
            if(exceptionCounter > 0) exceptionCounter--;
        } catch (ReportedException var11) {
            exceptionCounter++;
            if(exceptionCounter > 3) throw var11;
            LOGGER.error(LogUtils.FATAL_MARKER, "Reported exception thrown!", (Throwable)var11);
            // this.emergencySaveAndCrash(var11.getReport());
            this.emergencySave();
            CrashReport report = var11.getReport();
            saveReport(this.gameDirectory, report);
            setScreen(new MinecraftCrashedScreen(var11.getCause(), report));
            LOGGER.error(LogUtils.FATAL_MARKER, report.getFriendlyReport(ReportType.CRASH));
            LOGGER.error(LogUtils.FATAL_MARKER, "啊, 可莉炸客户端又被你发现了, 可莉又闯祸了...");
            LOGGER.error(LogUtils.FATAL_MARKER, "趁琴团长不在, 我帮你把客户端修好吧, 别告可莉状, 求求了qwq");
        } catch (Throwable var12) {
            exceptionCounter++;
            if(exceptionCounter > 3) throw var12;
            if (var12 instanceof OutOfMemoryError OOMError) {
//                if (bl) {
//                    throw OOMError;
//                }
//
//                this.emergencySave();
//                this.setScreen(new OutOfMemoryScreen());
//                System.gc();
//                LOGGER.error(LogUtils.FATAL_MARKER, "Out of memory", (Throwable) OOMError);
//                bl = true;
                throw OOMError; // 上部会处理OOM
            } else {
                LOGGER.error(LogUtils.FATAL_MARKER, "Unreported exception thrown!", var12);
                // this.emergencySaveAndCrash(new CrashReport("Unexpected error", var12));
                this.emergencySave();
                CrashReport report = new CrashReport("Unexpected error", var12);
                saveReport(this.gameDirectory, report);
                setScreen(new MinecraftCrashedScreen(var12, report));
                LOGGER.error(LogUtils.FATAL_MARKER, report.getFriendlyReport(ReportType.CRASH));
                LOGGER.error(LogUtils.FATAL_MARKER, "啊, 可莉炸客户端又被你发现了, 可莉又闯祸了...");
                LOGGER.error(LogUtils.FATAL_MARKER, "趁琴团长不在, 我帮你把客户端修好吧, 别告可莉状, 求求了qwq");
            }
        }
    }

    @Overwrite
    private void handleDelayedCrash() {
        if (this.delayedCrash != null) {
            LOGGER.error("在延迟崩溃中发现报告实例! " + delayedCrash.get());
            CrashReport crashReport = delayedCrash.get();
            delayedCrash = null;
            throw new ReportedException(crashReport);
        }
    }

    @Shadow
    public static int saveReport(File file, CrashReport crashReport) {
        return 0;
    }

    @Shadow
    public void emergencySaveAndCrash(CrashReport report) {
    }

    @Shadow
    public void setScreen(Screen screen) {
    }

    @Shadow
    private void emergencySave() {
    }

    @Shadow
    private void finishProfilers(boolean bl2, SingleTickProfiler singleTickProfiler) {
    }

    @Shadow
    private ProfilerFiller constructProfiler(boolean bl2, SingleTickProfiler singleTickProfiler) {
        return null;
    }

    @Shadow
    public DebugScreenOverlay getDebugOverlay() {
        return null;
    }

    @Shadow
    private void runTick(boolean b) {
    }

}