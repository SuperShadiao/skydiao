package pers.XiaoShadiao.skydiao.utils;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.fabric.FabricModAPI;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.Platform;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.mixin.client.MixinEntityCloneableAccessor;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ToolList {

    public static final Minecraft mc;

    static {
        mc = Minecraft.getInstance();
        if (mc == null) throw new AssertionError("不允许在Minecraft实例启动前加载ToolList, 检查一下代码看看。(如果处于运行环境, 请将该问题报告给小沙雕! " + SkyDiaoModClient.CONST_QQGROUP_MAIN + ")");
    }

    private static ToolList instance;

    private final boolean imXiaoShadiao = System.getenv().getOrDefault("USERNAME", "").equals("小沙雕");
    public Random random = new Random();
    private DevelopmentEnvironmentDetector devDetectorInstance;
    public Logger log = LogManager.getLogger("XSD Utils");


    public static ToolList getInstance() {
        if (instance == null) instance = new ToolList();
        return instance;
    }

    public static void destroy() {
        instance = null;
    }

    public static boolean isInSkyblock() {
        return "SkyBlock".equals(StatusManager.get().getType());
    }

    public boolean isXiaoShadiao() {
        return imXiaoShadiao;
    }

    public InputStream makeReqToURL(String url) {
        return makeReqToURL(url, false, null, null);
    }

    public InputStream makeReqToURL(String url, boolean allowErrorStream) {
        return makeReqToURL(url, allowErrorStream, null, null);
    }

    public InputStream makeReqToURL(String url, boolean allowErrorStream, Consumer<URLConnection> ucin, Consumer<String> onRedirect) {
        return makeReqToURL(url, allowErrorStream, ucin, onRedirect, false);
    }

    public InputStream makeReqToURL(String url, boolean allowErrorStream, Consumer<URLConnection> ucin, Consumer<String> onRedirect, boolean disableSSL) {

        if (url.contains("hypixelhelper.pages.dev")) url = url.replace("hypixelhelper.pages.dev", "xiaoshadiao.club");

        if (url.contains("mojang")) disableSSL = true;

        if (!url.contains("hypixel") || isDevEnvironment()) log.info(url);
        HttpURLConnection uc = null;
        try {
            uc = (HttpURLConnection) new URL(url/*.replace("http://", "https://")*/).openConnection();
            uc.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0");

            if (url.contains("https://www.gitlink.org.cn"))
                uc.addRequestProperty("Referer", "https://www.gitlink.org.cn/SuperShadiao/hypixelhelper");

            uc.setInstanceFollowRedirects(true);
            if (uc instanceof HttpsURLConnection) {
                if (disableSSL) {
                    ((HttpsURLConnection) uc).setSSLSocketFactory(HttpSSLDisabler.getTrustAll());
                    ((HttpsURLConnection) uc).setHostnameVerifier((h, s) -> true);
                } else {
                    ((HttpsURLConnection) uc).setSSLSocketFactory(HttpSSLDisabler.getDefault());
                }
            }
            uc.setConnectTimeout(30000);
            uc.setReadTimeout(30000);

            if (ucin != null) ucin.accept(uc);

            uc.connect();

            if (uc.getResponseCode() == 302) {
                log.info("网页重定向");
                String newUrl = uc.getHeaderField("Location");
                if (onRedirect != null) onRedirect.accept(newUrl);
                return makeReqToURL(newUrl, allowErrorStream, ucin, onRedirect);
            }
            return allowErrorStream && uc.getResponseCode() != 200 ? uc.getErrorStream() : uc.getInputStream();

        } catch (Exception e) {

            if (url.contains("//api.hypixel.net")) {
                try {
                    return makeReqToURL("https://xiaoshadiao.club/datagetter?url=" + URLEncoder.encode(url, "UTF-8"), allowErrorStream, ucin, onRedirect);
                } catch (UnsupportedEncodingException ex) {
                    throw new RuntimeException(ex);
                }
            }

//			else if(e.toString().contains("unable to find valid certification path to requested target")) {
//				log.warn("出现了非常逆天的问题: " + url + " -> " + e);
//				log.warn("使用外置程序请求网站!");
//				return 请求网站E(url);
//			}
            if (e instanceof javax.net.ssl.SSLException) {
                if (e.toString().contains("plaintext connection")) {
                    log.warn("出现了非常逆天的问题: " + url + " -> " + e);
                    log.warn("尝试强转http重试...");
                    return makeReqToURL(url.replace("https://", "http://"), allowErrorStream, ucin, onRedirect, true);
                }

                if (e instanceof javax.net.ssl.SSLHandshakeException && !disableSSL) {
                    log.warn("出现了非常逆天的问题: " + url + " -> " + e);
                    log.warn("尝试禁用SSL重试...");
                    return makeReqToURL(url, allowErrorStream, ucin, onRedirect, true);
                }
            }

            throw new RuntimeException("Exception in visiting \"" + url + "\" because " + e, e);
        }

    }

    public boolean stringHasContext(String str) {
        return str != null && !str.isEmpty();
    }

    public boolean isDevEnvironment() {

        if (devDetectorInstance == null) {

            if (true) {
                boolean temp = FabricLoader.getInstance().isDevelopmentEnvironment();
                if (temp) {
                    log.info("Develop环境");
                } else {
                    log.info("非Develop环境, 正式游戏");
                }
                return (devDetectorInstance = new DevelopmentEnvironmentDetector(temp)).isDevMode;
            }

            try {
                Minecraft.class.getDeclaredField("instance");
                log.info("Develop环境");
                devDetectorInstance = new DevelopmentEnvironmentDetector(true);
                return true;
            } catch (Exception e) {
                log.info("非Develop环境, 正式游戏");
                devDetectorInstance = new DevelopmentEnvironmentDetector(false);
            }
            return false;

        } else return devDetectorInstance.isDevMode;

    }

    public boolean verifyFileWithMD5(File file, String md5) {
        if (!file.exists()) return false;
        String realMD5 = getMD5(file);
        return realMD5.equals(md5);
    }

    public byte[] downloadFileWithMD5(String url, String md5) throws IOException {
        try (InputStream is = makeReqToURL(url, true)) {
            byte[] bytes = is.readAllBytes();

            String realMD5 = getMD5(bytes);
            if (realMD5.equals(md5)) {
                return bytes;

            } else {
                throw new IOException("MD5校验失败, Current: " + realMD5 + ", required: " + md5);
            }
        } catch (Exception e) {
            throw new IOException("无法下载文件", e);
        }
    }

    private String byteToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(32); // 预分配长度
        for (byte b : bytes) {
            int i = b & 0xFF;
            if (i < 16) {
                hexString.append('0');
            }
            hexString.append(Integer.toHexString(i));
        }
        return hexString.toString();
    }

    public String getMD5(File file) {
        try {
            return getMD5(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getMD5(InputStream inputStream) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192]; // 8KB缓冲区
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md5.update(buffer, 0, bytesRead); // 分块更新摘要
            }

            byte[] digest = md5.digest();
            return byteToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getMD5(byte[] bl) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return byteToHex(md5.digest(bl));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getMD5(String s, boolean utf8) {

        if (utf8) {
            return getMD5(s.getBytes(StandardCharsets.UTF_8));
        } else return getMD5(s.getBytes());

    }

    public String getMD5(String s) {
        return getMD5(s, true);
    }

    public String deleteColorCode(String string) {
        return string.replaceAll("§.", "");
    }

    public String getMidOfText(String target, String left, String right) {
        try {

            int j = 0, k = 0;
            boolean b = false;

            for (int i = 0; i < target.length() - (b ? right : left).length() + 1; i++) {
                if (target.startsWith(b ? right : left, i)) { //if(target.substring(i,i + (b ? right : left).length()).equals(b ? right : left)) {
                    if (!b) {
                        j = i + left.length();
                        i += left.length() - 1;
                        b = true;
                    } else {
                        k = i;
                        break;
                    }
                }
            }
            return k * j == 0 ? "" : target.substring(j, k);

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public double ezStringToNumber(String number) {
        if (number == null || number.isEmpty()) {
            throw new IllegalArgumentException("输入字符串不能为空");
        }

        // 获取最后一个字符（可能是后缀）
        char suffix = number.charAt(number.length() - 1);

        // 判断是否是数字字符
        if (Character.isDigit(suffix)) {
            // 如果没有后缀，直接转换为 double
            return Double.parseDouble(number);
        }

        // 提取数字部分
        String numericPart = number.substring(0, number.length() - 1);
        double value = Double.parseDouble(numericPart);

        // 根据后缀进行转换
        return switch (Character.toUpperCase(suffix)) {
            case 'K' -> // 千
                    value * 1_000;
            case 'M' -> // 百万
                    value * 1_000_000;
            case 'B' -> // 十亿
                    value * 1_000_000_000;
            case 'T' -> // 万亿
                    value * 1_000_000_000_000L;
            default -> throw new IllegalArgumentException("不支持的后缀: " + suffix);
        };
    }

    public String numberToEZString(double d) {
        String[] sl = {"", "k", "M", "B", "T"};
        double d2 = d;

        for (String s : sl) {
            d2 /= 1000;
            if (d2 < 1) {
                return (Math.round(d2 * 1000d * 100d) / 100d) + s;
            }
        }

        return (Math.round(d2 * 1000d * 100d) / 100d) + sl[sl.length - 1];
    }

    @Nullable
    public Component tryGetTitleFromPacket(Packet<?> packet) {
        return switch (packet) {
            case ClientboundSetTitleTextPacket titlePacket -> titlePacket.text();
            case ClientboundSetSubtitleTextPacket subTitlePacket -> subTitlePacket.text();
            default -> null;
        };
    }

    public void playSound(SoundEvent soundEvent) {
        if (mc.player != null) {
            mc.execute(() -> mc.player.playSound(soundEvent, 1.0F, 1.0F));
        }
    }

    public boolean isEntityOnWorld(Entity entity) {
        return mc.level != null && mc.level.getEntity(entity.getId()) == entity;
    }

    public HitResult predictPlayerAimBlock(Entity e) {
        return predictPlayerAimBlock(e.getEyePosition(), e.getYRot(), e.getXRot(), 3);
    }

    public HitResult predictPlayerAimBlock(Entity e, double blockReachDistance) {
        return predictPlayerAimBlock(e.getEyePosition(), e.getYRot(), e.getXRot(), blockReachDistance);
    }

    public HitResult predictPlayerAimBlock(Entity e, float yaw, float pitch, double blockReachDistance) {
        return predictPlayerAimBlock(e.getEyePosition(), yaw, pitch, blockReachDistance);
    }

    public HitResult predictPlayerAimBlock(Vec3 from, float yaw, float pitch, double blockReachDistance) {
        // int blockReachDistance = 10;

        float f = Mth.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = Mth.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -Mth.cos(-pitch * 0.017453292F);
        float f3 = Mth.sin(-pitch * 0.017453292F);

        Vec3 vec31 = new Vec3((double) (f1 * f2), (double) f3, (double) (f * f2));
        Vec3 vec32 = from.add(vec31.x * blockReachDistance, vec31.y * blockReachDistance, vec31.z * blockReachDistance);

        return mc.level.clip(new ClipContext(from, vec32, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));

    }

    public HitResult predictPlayerAimBlock(Vec3 from, Vec3 to) {
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    /**
     *  毫秒
     *  */
    public String timeToString(long time) {
        long l = time;
        l /= 1000;

        int day = (int) (l / (60 * 60 * 24));
        l -= day * (60 * 60 * 24);
        int hour = (int) (l / (3600));
        l -= hour * 3600L;
        int min = (int) (l / 60);
        l -= min * 60L;
        int sec = (int) l;

        return (day == 0 ? "" : day + "天") + (hour == 0 ? "" : hour + "小时") + (min == 0 ? "" : min + "分钟") + (sec == 0 ? "" : sec + "秒");
    }

    public boolean isFullBlock(BlockState blockState) {
        VoxelShape occlusionShape = blockState.getOcclusionShape();
        if(blockState.getBlock() == Blocks.AIR || occlusionShape.isEmpty()) return false;
        AABB bounds = occlusionShape.bounds();
        return bounds.maxX >= 1 && bounds.maxY >= 1 && bounds.maxZ >= 1;
    }

    private static final AABB zeroAABB = new AABB(0, 0, 0, 0, 0, 0);

    public boolean canThroughBlock(BlockState blockState) {
        VoxelShape occlusionShape = blockState.getOcclusionShape();
        // System.out.println(blockState.getBlock());
        // System.out.println(occlusionShape);
        if(blockState.getBlock() == Blocks.AIR || occlusionShape.isEmpty()) return true;
        AABB bounds = occlusionShape.bounds();
        // System.out.println(bounds);
        return zeroAABB.equals(bounds);
    }

    public boolean isEntityInArea(Entity entity, BlockPos pos1, BlockPos pos2) {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        return entity.getX() >= minX && entity.getX() <= maxX && entity.getY() >= minY && entity.getY() <= maxY && entity.getZ() >= minZ && entity.getZ() <= maxZ;
    }

    public Slot[][] mapSlotsToArray(ChestMenu menu) {
        int totalCount = (9 * menu.getRowCount());
        Slot[][] slots = new Slot[9][menu.getRowCount()];
        new Slot(null, 0, 0, 0);
        int i = 0;
        for (Slot slot : menu.slots) {
            if(i >= totalCount) break;
            if(slot != null && slot.container != mc.player.getInventory()) {
                slots[i % 9][i / 9] = slot;
            }
            i++;
        }
        return slots;
    }

    public String tryGetSkyblockItemId(ItemStack itemStack) {
        return itemStack.getComponents().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("id", "");
    }

    public void updatePartyInfo() {
        HypixelModAPI.getInstance().sendPacket(new ServerboundPartyInfoPacket());
    }

    public @NotNull <T extends Entity> T cloneEntity(T entity) {
        return (T) ((MixinEntityCloneableAccessor) entity).clone();
    }

    public record TPInfo(PositionMoveRotation from, PositionMoveRotation to) { }

    public TPInfo parseTPPacket(ClientboundPlayerPositionPacket tpPacket) {
        PositionMoveRotation player = PositionMoveRotation.of(mc.player);
        PositionMoveRotation to = PositionMoveRotation.calculateAbsolute(player, tpPacket.change(), tpPacket.relatives());
        return new TPInfo(player, to);
    }

    //    public static class DevelopmentEnvironmentDetector {
    //
    //        public final boolean isDevMode;
    //
    //        public DevelopmentEnvironmentDetector(boolean b) {
    //            isDevMode = b;
    //        }
    //
    //    }
    public record DevelopmentEnvironmentDetector(boolean isDevMode) {
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static class ThreadedTask<R> {

        public final Future<R> future;
        public final Callable<R> callable;
        public final Runnable runnable;
        public final R value;

        public ThreadedTask(Future<R> f, Callable<R> c) {
            future = Objects.requireNonNull(f);
            callable = Objects.requireNonNull(c);
            runnable = null;
            value = null;
        }

        public ThreadedTask(Future<R> f, Runnable c, R v) {
            future = Objects.requireNonNull(f);
            callable = null;
            runnable = Objects.requireNonNull(c);
            value = v;
        }

        public Object getOriginalTask() {
            if (runnable != null) return runnable;
            if (callable != null) return callable;
            throw new IllegalStateException("R & C both null");
        }

        public void reRun(Collection<ThreadedTask<?>> ttl) {
            reRunWithDelayMS(ttl, 0);
        }

        public void reRunWithDelayMS(Collection<ThreadedTask<?>> ttl, long ms) {
            ttl.add(addThreadedTask(() -> {
                R v;
                if (ms > 0) try {
                    Thread.sleep(ms);
                } catch (InterruptedException e) {
                }
                ;

                if (runnable != null) {
                    runnable.run();
                    v = value;
                } else if (callable != null) {
                    v = callable.call();
                } else {
                    throw new IllegalStateException("R & C both null");
                }
                return v;
            }));
        }

    }

    public static <R> ThreadedTask<R> addThreadedTask(Callable<R> c) {
        return new ThreadedTask<>(executor.submit(c), c);
    }

    public static <R> ThreadedTask<R> addThreadedTask(Runnable r, R obj) {
        return new ThreadedTask<>(executor.submit(r, obj), r, obj);
    }

    @SuppressWarnings("ConstantConditions")
    public static void printChatMessage(Component msg) {
        if (ToolList.mc != null && ToolList.mc.gui != null && ToolList.mc.gui.getChat() != null) {
            ToolList.mc.execute(() -> ToolList.mc.gui.getChat().addClientSystemMessage(msg));
        }
    }

    private final Long2LongOpenHashMap timeToLoad = new Long2LongOpenHashMap();

    public boolean isChunkLoaded(ClientLevel level, BlockPos pos) {
        if (level == null) return false;

        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;

        if (level.getBlockState(pos).getBlock() == Blocks.VOID_AIR) {
            return false;
        }

        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
        long packedChunkPos = ChunkPos.pack(cx, cz);

        if(true) return chunk != null && !chunk.isEmpty(); else return false;

//        if(chunk != null && !chunk.isEmpty()) {
//            if(timeToLoad.containsKey(packedChunkPos)) {
//                if (System.currentTimeMillis() - timeToLoad.get(packedChunkPos) > 1000) {
//                    timeToLoad.remove(packedChunkPos);
//                    return true;
//                } else return false;
//            }
//            return true;
//        } else {
//            timeToLoad.put(packedChunkPos, System.currentTimeMillis());
//        }
//        return false;
    }


    public static void sendChatMessage(String msg) {
        if (mc.player != null) {
            if (msg.startsWith("/")) {
                mc.player.connection.sendCommand(msg.substring(1));
            } else {
                mc.player.connection.sendChat(msg);
            }
        }
    }

    public String encodeString(String message) {
        char key = (char) random.nextInt(0x10000);
        char key2 = (char) (key ^ 2888);
        char[] bl = message.toCharArray();
        for (int i = 0; i < bl.length; i++) {
            bl[i] = (char) (bl[i] ^ key ^ ((i * key2 + 100) % 0x10000));
        }

        String s = key + new String(bl) + key2;
        if (!decodeString(s).equals(message)) {
            throw new AssertionError("加密时出错");
        }
        return s;
    }

    public String decodeString(String message) {

        try {
            String decodeString = message;
            if (decodeString == null) return null;
            if (decodeString.isEmpty()) return "";
            char key1 = decodeString.substring(0, 1).charAt(0);
            decodeString = decodeString.substring(1);
            char key2 = decodeString.substring(decodeString.length() - 1, decodeString.length()).charAt(0);
            // log.info(key1);

            if ((key1 ^ key2) != 2888) return null;

            char[] newchars = new char[decodeString.length() - 1];
            char[] oldchars = decodeString.toCharArray();
            for (int i = 0; i < decodeString.length() - 1; i++) {
                newchars[i] = (char) (oldchars[i] ^ key1 ^ ((i * key2 + 100) % 0x10000));
            }

            return new String(newchars);
        } catch (Exception e) {
            return null;
        }

    }

    public static boolean hasGlint(ItemStack stack) {
        return Optional.ofNullable(stack.getComponentsPatch().get(stack.getComponents(), DataComponents.ENCHANTMENT_GLINT_OVERRIDE)).isPresent();
    }

    private static final Comparator<PlayerScoreEntry> scoreEntryComparator = Comparator.comparing(PlayerScoreEntry::value)
            .reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private Objective getScoreboardObjective() {
        if (mc.level == null || mc.player == null) return null;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = null;
        PlayerTeam playerTeam = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (playerTeam != null) {
            DisplaySlot displaySlot = DisplaySlot.teamColorToSlot(playerTeam.getColor());
            if (displaySlot != null) {
                objective = scoreboard.getDisplayObjective(displaySlot);
            }
        }
        return objective != null ? objective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    public Component getScoreboardTitle() {
        if (mc.level == null || mc.player == null) return Component.empty();
        Objective objective2 = getScoreboardObjective();
        if (objective2 != null) {
            return objective2.getDisplayName();
        }
        return Component.empty();
    }

    public String getScoreboardTitleNoColor() {
        return deleteColorCode(getScoreboardTitle().getString());
    }

    public List<Component> fetchScoreboardLines() {
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective2 = getScoreboardObjective();
        if (objective2 != null) {
            List<Component> lines = new ArrayList<>();
//            Collection<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective2);
//            for (PlayerScoreEntry entry : entries) {
//                PlayerTeam playerTeam2 = scoreboard.getPlayersTeam(entry.owner());
//                Component componentx2 = entry.ownerName();
//                Component component2 = PlayerTeam.formatNameForTeam(playerTeam2, componentx2);
//                NumberFormat numberFormat = objective2.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
//                Component component3 = entry.formatValue(numberFormat);
//                lines.add(Component.empty().append(componentx2).append(component2).append(component3));
//            }
            NumberFormat numberFormat = objective2.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
            scoreboard.listPlayerScores(objective2)
                    .stream()
                    .filter(playerScoreEntry -> !playerScoreEntry.isHidden())
                    .sorted(scoreEntryComparator)
                    .limit(15L)
                    .forEach(playerScoreEntry -> {
                        PlayerTeam playerTeam2 = scoreboard.getPlayersTeam(playerScoreEntry.owner());
                        Component componentx = playerScoreEntry.ownerName();
                        Component component2 = PlayerTeam.formatNameForTeam(playerTeam2, componentx);
                        Component component3 = playerScoreEntry.formatValue(numberFormat);
                        lines.add(Component.empty().append(componentx).append(component2).append(component3));
                    });
            return lines;
        }
        return List.of();
    }

    public List<String> fetchScoreboardLinesNoColor() {
        return fetchScoreboardLines().stream().map(Component::getString).map(this::deleteColorCode).collect(Collectors.toList());
    }

    public void printComponent(Component component) {
        if(component instanceof MutableComponent m) {
            new Consumer<MutableComponent>() {
                @Override
                public void accept(MutableComponent c) {
                    c.getContents().visit(d -> {
                        System.out.println(d);
                        return Optional.empty();
                    });
                    System.out.println(c.getContents().getClass());
                    c.getSiblings().forEach(a -> {
                        if (a instanceof MutableComponent m) accept(m);
                    });
                }
            }.accept(m);
            System.out.println(m.getString());
        }
    }

    public volatile boolean isProxyAccessible = false;
    private final Object proxyAccessibleLock = new Object();

    public String wrapAsGithubProxy(String url) {
        try {
            return wrapAsGithubProxy(new URL(url)).toString();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public URL wrapAsGithubProxy(URL url) {
        try {
            return wrapAsGithubProxy(url.toURI()).toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public URI wrapAsGithubProxy(URI url) {
        if (url.getHost().contains("github.com")) {
            if(!isProxyAccessible) {
                synchronized (proxyAccessibleLock) {
                    if(!isProxyAccessible) {
                        try(InputStream is = ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club")) {
                            is.readAllBytes();
                            isProxyAccessible = true;
                        } catch (Throwable e) {
                            e.printStackTrace();
                            return url;
                        }
                    }
                }
            }
            String proxy = "https://xiaoshadiao.club/datagetter?url=" + URLEncoder.encode(url.toString(), StandardCharsets.UTF_8);
            ToolList.getInstance().log.info("成功包装Github Proxy链接: " + proxy);
            url = URI.create(proxy);
        }
        return url;
    }

}
