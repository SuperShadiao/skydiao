package pers.XiaoShadiao.skydiao.mixin.client;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.mojang.blaze3d.TracyBootstrap;
import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import com.mojang.util.UndashedUuid;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.*;
import net.minecraft.client.ClientBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.main.Main;
import net.minecraft.client.main.SilentInitException;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.client.telemetry.events.GameLoadTimesEvent;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.jfr.Environment;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.XSDSafeSession;

import java.io.File;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Mixin(value = Main.class, priority = Integer.MIN_VALUE)
public class MixinUserDataSecurer {

    // 尝试覆写所有方法来保证Main入口未被修改。

    @Overwrite
    public static void main(final String[] args) {
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.accepts("demo");
        parser.accepts("disableMultiplayer");
        parser.accepts("disableChat");
        parser.accepts("fullscreen");
        parser.accepts("checkGlErrors");
        OptionSpec<Void> renderDebugLabelsOption = parser.accepts("renderDebugLabels");
        OptionSpec<Void> jfrProfilingOption = parser.accepts("jfrProfile");
        OptionSpec<Void> tracyProfilingOption = parser.accepts("tracy");
        OptionSpec<Void> tracyNoImageOption = parser.accepts("tracyNoImages");
        OptionSpec<String> quickPlayPathOption = parser.accepts("quickPlayPath").withRequiredArg();
        OptionSpec<String> quickPlaySingleplayerOption = parser.accepts("quickPlaySingleplayer").withOptionalArg();
        OptionSpec<String> quickPlayMultiplayerOption = parser.accepts("quickPlayMultiplayer").withRequiredArg();
        OptionSpec<String> quickPlayRealmsOption = parser.accepts("quickPlayRealms").withRequiredArg();
        OptionSpec<File> gameDirOption = parser.accepts("gameDir").withRequiredArg().<File>ofType(File.class).defaultsTo(new File("."));
        OptionSpec<File> assetsDirOption = parser.accepts("assetsDir").withRequiredArg().ofType(File.class);
        OptionSpec<File> resourcePackDirOption = parser.accepts("resourcePackDir").withRequiredArg().ofType(File.class);
        OptionSpec<String> proxyHostOption = parser.accepts("proxyHost").withRequiredArg();
        OptionSpec<Integer> proxyPortOption = parser.accepts("proxyPort").withRequiredArg().defaultsTo("8080").ofType(Integer.class);
        OptionSpec<String> proxyUserOption = parser.accepts("proxyUser").withRequiredArg();
        OptionSpec<String> proxyPassOption = parser.accepts("proxyPass").withRequiredArg();
        OptionSpec<String> usernameOption = parser.accepts("username").withRequiredArg().defaultsTo("Player" + System.currentTimeMillis() % 1000L);
        OptionSpec<Void> offlineDeveloperMode = parser.accepts("offlineDeveloperMode");
        OptionSpec<String> uuidOption = parser.accepts("uuid").withRequiredArg();
        OptionSpec<String> xuidOption = parser.accepts("xuid").withOptionalArg().defaultsTo("");
        OptionSpec<String> clientIdOption = parser.accepts("clientId").withOptionalArg().defaultsTo("");
        OptionSpec<String> accessTokenOption = parser.accepts("accessToken").withRequiredArg().required();
        OptionSpec<String> versionOption = parser.accepts("version").withRequiredArg().required();
        OptionSpec<Integer> widthOption = parser.accepts("width").withRequiredArg().<Integer>ofType(Integer.class).defaultsTo(854);
        OptionSpec<Integer> heightOption = parser.accepts("height").withRequiredArg().<Integer>ofType(Integer.class).defaultsTo(480);
        OptionSpec<Integer> fullscreenWidthOption = parser.accepts("fullscreenWidth").withRequiredArg().ofType(Integer.class);
        OptionSpec<Integer> fullscreenHeightOption = parser.accepts("fullscreenHeight").withRequiredArg().ofType(Integer.class);
        OptionSpec<String> assetIndexOption = parser.accepts("assetIndex").withRequiredArg();
        OptionSpec<String> versionTypeString = parser.accepts("versionType").withRequiredArg().defaultsTo("release");
        OptionSpec<String> nonOption = parser.nonOptions();
        OptionSet optionSet = parser.parse(args);
        File gameDir = parseArgument(optionSet, gameDirOption);
        String launchedVersion = parseArgument(optionSet, versionOption);
        String stage = "Pre-bootstrap";

        Logger logger;
        GameConfig gameConfig;
        try {
            if (optionSet.has(jfrProfilingOption)) {
                JvmProfiler.INSTANCE.start(Environment.CLIENT);
            }

            if (optionSet.has(tracyProfilingOption)) {
                TracyBootstrap.setup();
            }

            Stopwatch totalTimePreClassLoadTimer = Stopwatch.createStarted(Ticker.systemTicker());
            Stopwatch preWindowPreClassLoadTimer = Stopwatch.createStarted(Ticker.systemTicker());
            GameLoadTimesEvent.INSTANCE.beginStep(TelemetryProperty.LOAD_TIME_TOTAL_TIME_MS, totalTimePreClassLoadTimer);
            GameLoadTimesEvent.INSTANCE.beginStep(TelemetryProperty.LOAD_TIME_PRE_WINDOW_MS, preWindowPreClassLoadTimer);
            SharedConstants.tryDetectVersion();
            TracyClient.reportAppInfo("Minecraft Java Edition " + SharedConstants.getCurrentVersion().name());
            CompletableFuture<?> dataFixerOptimization = DataFixers.optimize(DataFixTypes.TYPES_FOR_LEVEL_LIST);
            CrashReport.preload();
            logger = LogUtils.getLogger();
            stage = "Bootstrap";
            Bootstrap.bootStrap();
            ClientBootstrap.bootstrap();
            GameLoadTimesEvent.INSTANCE.setBootstrapTime(Bootstrap.bootstrapDuration.get());
            Bootstrap.validate();
            stage = "Argument parsing";
            List<String> leftoverArgs = optionSet.valuesOf(nonOption);
            if (!leftoverArgs.isEmpty()) {
                logger.info("Completely ignored arguments: {}", leftoverArgs);
            }

            String hostName = parseArgument(optionSet, proxyHostOption);
            Proxy proxy = Proxy.NO_PROXY;
            if (hostName != null) {
                try {
                    proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(hostName, parseArgument(optionSet, proxyPortOption)));
                } catch (Exception var74) {
                }
            }

            final String proxyUser = parseArgument(optionSet, proxyUserOption);
            final String proxyPass = parseArgument(optionSet, proxyPassOption);
            if (!proxy.equals(Proxy.NO_PROXY) && stringHasValue(proxyUser) && stringHasValue(proxyPass)) {
                Authenticator.setDefault(new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
                    }
                });
            }

            int width = parseArgument(optionSet, widthOption);
            int height = parseArgument(optionSet, heightOption);
            OptionalInt fullscreenWidth = ofNullable(parseArgument(optionSet, fullscreenWidthOption));
            OptionalInt fullscreenHeight = ofNullable(parseArgument(optionSet, fullscreenHeightOption));
            boolean isFullscreen = optionSet.has("fullscreen");
            boolean isDemo = optionSet.has("demo");
            boolean disableMultiplayer = optionSet.has("disableMultiplayer");
            boolean disableChat = optionSet.has("disableChat");
            boolean captureTracyImages = !optionSet.has(tracyNoImageOption);
            boolean renderDebugLabels = optionSet.has(renderDebugLabelsOption);
            String versionType = parseArgument(optionSet, versionTypeString);
            File assetsDir = optionSet.has(assetsDirOption) ? parseArgument(optionSet, assetsDirOption) : new File(gameDir, "assets/");
            File resourcePackDir = optionSet.has(resourcePackDirOption) ? parseArgument(optionSet, resourcePackDirOption) : new File(gameDir, "resourcepacks/");
            UUID uuid = hasValidUuid(uuidOption, optionSet, logger)
                    ? UndashedUuid.fromStringLenient(uuidOption.value(optionSet))
                    : UUIDUtil.createOfflinePlayerUUID(usernameOption.value(optionSet));
            String assetIndex = optionSet.has(assetIndexOption) ? assetIndexOption.value(optionSet) : null;
            String xuid = optionSet.valueOf(xuidOption);
            String clientId = optionSet.valueOf(clientIdOption);
            String quickPlayLogPath = parseArgument(optionSet, quickPlayPathOption);
            GameConfig.QuickPlayVariant quickPlayVariant = getQuickPlayVariant(optionSet, quickPlaySingleplayerOption, quickPlayMultiplayerOption, quickPlayRealmsOption);
            User user = new User(
                    usernameOption.value(optionSet), uuid, accessTokenOption.value(optionSet), emptyStringToEmptyOptional(xuid), emptyStringToEmptyOptional(clientId)
            );

            // ============= Wrap =============
            user = new XSDSafeSession(user);
            // ============= Wrap =============

            gameConfig = new GameConfig(
                    new GameConfig.UserData(user, proxy),
                    new DisplayData(width, height, fullscreenWidth, fullscreenHeight, isFullscreen),
                    new GameConfig.FolderData(gameDir, resourcePackDir, assetsDir, assetIndex),
                    new GameConfig.GameData(
                            isDemo, launchedVersion, versionType, disableMultiplayer, disableChat, captureTracyImages, renderDebugLabels, optionSet.has(offlineDeveloperMode)
                    ),
                    new GameConfig.QuickPlayData(quickPlayLogPath, quickPlayVariant)
            );
            Util.startTimerHackThread();
            dataFixerOptimization.join();
        } catch (Throwable var75) {
            CrashReport report = CrashReport.forThrowable(var75, stage);
            CrashReportCategory initialization = report.addCategory("Initialization");
            NativeModuleLister.addCrashSection(initialization);
            Minecraft.fillReport(null, null, launchedVersion, null, report);
            Minecraft.crash(null, gameDir, report);
            return;
        }

        Thread shutdownThread = new Thread("Client Shutdown Thread") {
            public void run() {
                Minecraft instance = Minecraft.getInstance();
                if (instance != null) {
                    IntegratedServer server = instance.getSingleplayerServer();
                    if (server != null) {
                        server.halt(true);
                    }
                }
            }
        };
        shutdownThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(logger));
        Runtime.getRuntime().addShutdownHook(shutdownThread);
        Minecraft newMinecraft = null;

        try {
            Thread.currentThread().setName("Render thread");
            RenderSystem.initRenderThread();
            newMinecraft = new Minecraft(gameConfig);
        } catch (SilentInitException var72) {
            Util.shutdownExecutors();
            logger.warn("Failed to create window: ", (Throwable)var72);
            return;
        } catch (Throwable var73) {
            CrashReport report = CrashReport.forThrowable(var73, "Initializing game");
            CrashReportCategory initialization = report.addCategory("Initialization");
            NativeModuleLister.addCrashSection(initialization);
            Minecraft.fillReport(newMinecraft, null, gameConfig.game.launchVersion, null, report);
            Minecraft.crash(newMinecraft, gameConfig.location.gameDirectory, report);
            return;
        }

        Minecraft minecraft = newMinecraft;
        newMinecraft.run();

        try {
            minecraft.stop();
        } finally {
            newMinecraft.destroy();
        }
    }

    @Overwrite
    private static GameConfig.QuickPlayVariant getQuickPlayVariant(
            final OptionSet optionSet,
            final OptionSpec<String> quickPlaySingleplayerOption,
            final OptionSpec<String> quickPlayMultiplayerOption,
            final OptionSpec<String> quickPlayRealmsOption
    ) {
        long enabledOptions = Stream.of(quickPlaySingleplayerOption, quickPlayMultiplayerOption, quickPlayRealmsOption).filter(optionSet::has).count();
        if (enabledOptions == 0L) {
            return GameConfig.QuickPlayVariant.DISABLED;
        } else if (enabledOptions > 1L) {
            throw new IllegalArgumentException("Only one quick play option can be specified");
        } else if (optionSet.has(quickPlaySingleplayerOption)) {
            String worldId = unescapeJavaArgument(parseArgument(optionSet, quickPlaySingleplayerOption));
            return new GameConfig.QuickPlaySinglePlayerData(worldId);
        } else if (optionSet.has(quickPlayMultiplayerOption)) {
            String serverAddress = unescapeJavaArgument(parseArgument(optionSet, quickPlayMultiplayerOption));
            return (GameConfig.QuickPlayVariant)Optionull.mapOrDefault(serverAddress, GameConfig.QuickPlayMultiplayerData::new, GameConfig.QuickPlayVariant.DISABLED);
        } else if (optionSet.has(quickPlayRealmsOption)) {
            String realmId = unescapeJavaArgument(parseArgument(optionSet, quickPlayRealmsOption));
            return (GameConfig.QuickPlayVariant)Optionull.mapOrDefault(realmId, GameConfig.QuickPlayRealmsData::new, GameConfig.QuickPlayVariant.DISABLED);
        } else {
            return GameConfig.QuickPlayVariant.DISABLED;
        }
    }

    @Overwrite
    @org.jspecify.annotations.Nullable
    private static String unescapeJavaArgument(@org.jspecify.annotations.Nullable final String arg) {
        return arg == null ? null : StringEscapeUtils.unescapeJava(arg);
    }

    @Overwrite
    private static Optional<String> emptyStringToEmptyOptional(final String xuid) {
        return xuid.isEmpty() ? Optional.empty() : Optional.of(xuid);
    }

    @Overwrite
    private static OptionalInt ofNullable(@org.jspecify.annotations.Nullable final Integer value) {
        return value != null ? OptionalInt.of(value) : OptionalInt.empty();
    }

    @Overwrite
    @org.jspecify.annotations.Nullable
    private static <T> T parseArgument(final OptionSet optionSet, final OptionSpec<T> optionSpec) {
        try {
            return optionSet.valueOf(optionSpec);
        } catch (Throwable var5) {
            if (optionSpec instanceof ArgumentAcceptingOptionSpec<T> options) {
                List<T> defaultValues = options.defaultValues();
                if (!defaultValues.isEmpty()) {
                    return (T)defaultValues.get(0);
                }
            }

            throw var5;
        }
    }

    @Overwrite
    private static boolean stringHasValue(@Nullable final String string) {
        return string != null && !string.isEmpty();
    }

    @Overwrite
    private static boolean hasValidUuid(final OptionSpec<String> uuidOption, final OptionSet optionSet, final Logger logger) {
        return optionSet.has(uuidOption) && isUuidValid(uuidOption, optionSet, logger);
    }

    @Overwrite
    private static boolean isUuidValid(final OptionSpec<String> uuidOption, final OptionSet optionSet, final Logger logger) {
        try {
            UndashedUuid.fromStringLenient(uuidOption.value(optionSet));
            return true;
        } catch (IllegalArgumentException var4) {
            logger.warn("Invalid UUID: '{}", uuidOption.value(optionSet));
            return false;
        }
    }

}
