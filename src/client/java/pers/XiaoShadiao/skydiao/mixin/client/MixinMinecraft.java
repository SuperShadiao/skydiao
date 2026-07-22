package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.blaze3d.GpuOutOfMemoryException;
import com.mojang.logging.LogUtils;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.Services;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.SingleTickProfiler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.screen.MinecraftCrashedScreen;
import pers.XiaoShadiao.skydiao.utils.ClientRenderCrashFixer;
import pers.XiaoShadiao.skydiao.utils.MCThreadDumper;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;

import java.io.File;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow
    private int rightClickDelay;
    @Shadow
    public int missTime;
    @Shadow
    static Minecraft instance;
    @Final
    @Shadow
    private static Logger LOGGER;
    @Mutable
    @Final
    @Shadow
    private User user;
    @Final
    @Shadow
    public File gameDirectory;
    @Mutable
    @Final
    @Shadow
    private CompletableFuture<ProfileResult> profileFuture;
    @Final
    @Shadow
    private Services services;
    @Mutable
    @Final
    @Shadow
    private ClientTelemetryManager telemetryManager;
    @Mutable
    @Final
    @Shadow
    private UserApiService userApiService;
    @Mutable
    @Final
    @Shadow
    private ProfileKeyPairManager profileKeyPairManager;
    @Mutable
    @Final
    @Shadow
    private SplashManager splashManager;
    @Final
    @Shadow
    private Proxy proxy;

    // =========== SHADOW END =============

    @Unique
    private int exceptionCounter;
    @Unique
    private final int MAX_EXCEPTION_COUNTER = 10;;
    @Unique
    private MCThreadDumper dumperThread;

    @Inject(at = @At("HEAD"), method = "run")
    private void init(CallbackInfo info) {
        // This code is injected into the start of Minecraft.run()V
    }

    @Overwrite
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        Minecraft mc = (Minecraft) (Object) this;
        if (!user.equals(this.user)) {
            try { ChatClientManager.getChatClient().socket.close(); } catch (Exception ignored) {}
        }
        MixinRealmStatusReset.setFuture(null);
        MixinRealmsClientReseter.setRealmsClientInstance(null);
        this.user = user;
        this.profileFuture = CompletableFuture.supplyAsync(() -> this.services.sessionService().fetchProfile(this.user.getProfileId(), true), Util.nonCriticalIoPool());

        this.userApiService = new YggdrasilAuthenticationService(this.proxy).createUserApiService(user.getAccessToken());
        this.telemetryManager = new ClientTelemetryManager(mc, this.userApiService, this.user);
        this.profileKeyPairManager = ProfileKeyPairManager.create(this.userApiService, this.user, mc.gameDirectory.toPath());
        this.splashManager = new SplashManager(this.user);

        try {
            Class<?> clazz = Class.forName("de.hysky.skyblocker.utils.ApiAuthentication");
            Method method = clazz.getDeclaredMethod("updateToken");
            method.setAccessible(true);
            method.invoke(null);
        } catch (Throwable e) {
            if(!(e instanceof ClassNotFoundException)) {
                e.printStackTrace();
            }
        }
    }

    @WrapOperation(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"), method = "run")
    public void run(Minecraft instance, boolean bl, Operation<Void> original) {
        try {
            if(ToolList.getInstance().isXiaoShadiao()) {
                if(dumperThread == null) {
                    Thread thread = Thread.currentThread();
                    dumperThread = MCThreadDumper.INSTANCE;
                    dumperThread.setMinecraftThread(thread);
                }
                dumperThread.flagAlive();
            }
            original.call(instance, bl);
            if(exceptionCounter > 0) exceptionCounter--;
        } catch (ReportedException var11) {
            exceptionCounter++;
            if(exceptionCounter > MAX_EXCEPTION_COUNTER) throw var11;
            LOGGER.error(LogUtils.FATAL_MARKER, "Reported exception thrown!", (Throwable)var11);
            ProfilerFiller profilerFiller = Profiler.get();
            if(profilerFiller instanceof ActiveProfiler activeProfiler) {
                String path = ((MixinAcviteProfilePathAccessor) activeProfiler).getPath();
                LOGGER.error(LogUtils.FATAL_MARKER, "Game crashed on the profile path: " + path);
            }
            // this.emergencySaveAndCrash(var11.getReport());
            this.emergencySave();
            ClientRenderCrashFixer.fix();
            CustomRenderPipeline.closeAll();
            CrashReport report = var11.getReport();
            saveReport0(this.gameDirectory, report);
            setScreen(new MinecraftCrashedScreen(var11.getCause(), report));
            LOGGER.error(LogUtils.FATAL_MARKER, report.getFriendlyReport(ReportType.CRASH));
            LOGGER.error(LogUtils.FATAL_MARKER, "啊, 可莉炸客户端又被你发现了, 可莉又闯祸了...");
            LOGGER.error(LogUtils.FATAL_MARKER, "趁琴团长不在, 我帮你把客户端修好吧, 别告可莉状, 求求了qwq");
        } catch (Throwable var12) {
            exceptionCounter++;
            if(exceptionCounter > MAX_EXCEPTION_COUNTER) throw var12;
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
                if(var12 instanceof GpuOutOfMemoryException) {
                    LOGGER.error(LogUtils.FATAL_MARKER, "神秘异常出现了, 让小沙雕睡15s");
                    try { Thread.sleep(15000); } catch(InterruptedException ignored) {}
                }
                LOGGER.error(LogUtils.FATAL_MARKER, "Unreported exception thrown!", var12);
                // this.emergencySaveAndCrash(new CrashReport("Unexpected error", var12));
                this.emergencySave();
                ClientRenderCrashFixer.fix();
                CustomRenderPipeline.closeAll();
                CrashReport report = new CrashReport("Unexpected error", var12);
                saveReport0(this.gameDirectory, report);
                setScreen(new MinecraftCrashedScreen(var12, report));
                LOGGER.error(LogUtils.FATAL_MARKER, report.getFriendlyReport(ReportType.CRASH));
                LOGGER.error(LogUtils.FATAL_MARKER, "啊, 可莉炸客户端又被你发现了, 可莉又闯祸了...");
                LOGGER.error(LogUtils.FATAL_MARKER, "趁琴团长不在, 我帮你把客户端修好吧, 别告可莉状, 求求了qwq");
            }
        }
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    public void handleKeyBinds(Minecraft instance, boolean bl, Operation<Void> original) {
        boolean click = bl || InputSimulator.leftClickFlagMinecraft;
        boolean executed = false;
        if(!bl && missTime > 1000) {
            InputSimulator.continueAttack(click);
            executed = click;
        }
        if(!executed) {
            original.call(instance, click);
            if(missTime == 0) {
                InputSimulator.missTime = 0;
            }
        }
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    public boolean handleKeyBinds2(KeyMapping instance, Operation<Boolean> original) {
        if(InputSimulator.rightClickDelay != 0) {
            this.rightClickDelay = InputSimulator.rightClickDelay;
        }
        if(instance == ToolList.mc.options.keyUse) {
            return original.call(instance) || (InputSimulator.isMouseRightHolding && !InputSimulator.hasRemainRightClick() && !InputSimulator.isInventoryOpen());
        }
        return original.call(instance);
    }

    @WrapOperation(method = "updateTitle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;createTitle()Ljava/lang/String;"))
    public String updateTitle(Minecraft instance, Operation<String> original) {
        return AbstractListener.titleChanger.updateMCTitle(original.call(instance));
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    public void startUseItem(CallbackInfo ci) {
        InputSimulator.rightClickDelay = this.rightClickDelay;
    }

    @Unique
    private static int saveReport0(final File gameDirectory, final CrashReport crash) {
        Path crashDir = gameDirectory.toPath().resolve("crash-reports");
        Path crashFile = crashDir.resolve("crash-" + Util.getFilenameFormattedDateTime() + "-client.txt");
        Bootstrap.realStdoutPrintln(crash.getFriendlyReport(ReportType.CRASH));
        LOGGER.debug("Disabling console - remaining logs will be available only in log file");

        byte var4;
        if (crash.getSaveFile() != null) {
            Bootstrap.realStdoutPrintln("#@!@# Game crashed! Crash report saved to: #@!@# " + crash.getSaveFile().toAbsolutePath());
            return -1;
        }

        if (!crash.saveToFile(crashFile, ReportType.CRASH)) {
            Bootstrap.realStdoutPrintln("#@?@# Game crashed! Crash report could not be saved. #@?@#");
            return -2;
        }

        Bootstrap.realStdoutPrintln("#@!@# Game crashed! Crash report saved to: #@!@# " + crashFile.toAbsolutePath());
        var4 = -1;

        // 这里有关闭stdOut的操作, 但是因为防崩端, 还是需要使用, 所以不要关闭

        return var4;
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