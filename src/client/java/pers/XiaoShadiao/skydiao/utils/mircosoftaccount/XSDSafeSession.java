package pers.XiaoShadiao.skydiao.utils.mircosoftaccount;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.util.UndashedUuid;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import sun.misc.Unsafe;

import java.io.UnsupportedEncodingException;
import java.lang.constant.Constable;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class XSDSafeSession extends User {

    private static String fakeToken;

    static {
        // fakeToken = new FakeString();
        FakeString fakeString = new FakeString();

        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Field field = XSDSafeSession.class.getDeclaredField("fakeToken");
            long staticFieldOffset = unsafe.staticFieldOffset(field);
            unsafe.putObject(XSDSafeSession.class, staticFieldOffset, fakeString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final Object2IntMap<String> stackPrintCount = new Object2IntOpenHashMap<>();

    private static final List<UUID> requestedBypassList = List.of(
            // UUID.fromString("3f448a12-a2b3-46ef-9a46-ca145b2c9550")
    );

    private static final Logger log = LogManager.getLogger();

    public XSDSafeSession(String name, UUID uuid, String accessToken) {
        super(name, uuid, encodeString(accessToken), ToolList.mc.getUser().getXuid(), ToolList.mc.getUser().getClientId());
        if(!toString().equals(super.toString())) throw new AssertionError("!?强强?!");
    }

    public XSDSafeSession(User parent) {
        super(parent.getName(), parent.getProfileId(), encodeString(parent.getAccessToken()), parent.getXuid(), parent.getClientId());
        if(!toString().equals(super.toString())) throw new AssertionError("!?强强?!");
    }

    private Object[] checkStack() {
        if(!toString().equals(super.toString())) throw new AssertionError("!?强强?!");
        StackTraceElement[] ste = new Throwable().getStackTrace();
        boolean flag = false;
        int deepth = 2;
        String stack = ste[deepth].getClassName();

        List<Class<?>> acceptClass = getAcceptableClasses();

        Optional<Class<?>> opt = acceptClass.stream().filter(c -> c.getName().equals(stack)).findAny();
        boolean isFullySafe = opt.isPresent();
        if (isFullySafe) flag = true;
        if (stack.startsWith("gg.essential")) {
            return new Object[] {false, stack, null}; // 艾斯比 mod。
        }

        if (requestedBypassList.contains(getProfileId())) {
            flag = true;
            log.info("有人尝试获取token, 但根据你的请求, 小沙雕不再保护你的token, 一切后果由你自己承担");
        }

        if (!flag) {
            new Throwable().printStackTrace();
        }

        return new Object[] {flag, stack, opt.orElse(null)};
    }

    @Override
    public @NotNull String getAccessToken() {
        Object[] objects = checkStack();
        boolean allow = (boolean) objects[0];
        String stack = (String) objects[1];

        if (allow) {
            printGetterStack("getAccessToken", stack);
            return ToolList.getInstance().decodeString(super.getAccessToken());
        }

        throwException("getAccessToken", stack);
        return fakeToken;
    }

    @Override
    public @NotNull String getSessionId() {
        Object[] objects = checkStack();
        if(objects[2] == Minecraft.class) {
            String tk = ToolList.getInstance().decodeString(super.getAccessToken());
            char[] charArray = tk.toCharArray();
            try {
                Arrays.fill(charArray, 2, charArray.length - 2, '*');
            } catch (Exception e) {
                Arrays.fill(charArray, '*');
            }
            tk = new String(charArray);
            String s = "token:" + tk + ":" + UndashedUuid.toString(this.getProfileId());
            log.warn("已移除Minecraft类在Debug日志下打印的AccessToken");
            return s;
        }
        boolean allow = (boolean) objects[0];
        String stack = (String) objects[1];

        if (allow) {
            // public String getSessionId() {
            //		return "token:" + this.accessToken + ":" + UndashedUuid.toString(this.uuid);
            //	}
            printGetterStack("getSessionId", stack);
            return "token:" + ToolList.getInstance().decodeString(super.getAccessToken()) + ":" + UndashedUuid.toString(this.getProfileId());
        }


        throwException("getSessionId", stack);
        return fakeToken;
    }

    public boolean equals(Object session) {
        if (session instanceof User user) {
            return user.getProfileId().equals(this.getProfileId()) && user.getName().equals(this.getName());
        }
        return false;
    }

    private void printGetterStack(String str, String stack) {
        log.warn("椎栈" + stack + "在刚才调用了" + str + "()");
        if(stackPrintCount.computeInt(stack, (_, value) -> (value == null ? 0 : value) + 1) < 4) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e椎栈" + stack + "在刚才通过" + str + "()获取了你的Token"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e可能来自于Mod: §6" + getSuspiciousInfo(stack)));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e该椎栈位于小沙雕白名单内, 你无需过多担心"));
        }
    }

    private void throwException(String str, String stack) {
        if(stackPrintCount.computeInt(stack, (_, value) -> (value == null ? 0 : value) + 1) < 4) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c未经授权的椎栈" + stack + "尝试通过" + str + "()获取你的Token, 已进行拦截"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c可能来自于Mod: §6" + getSuspiciousInfo(stack)));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c如果你不认识该Mod, 可能为恶意模组, 请立即删除"));
        }
    }

    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }

    private static String encodeString(String message) {
        char key = (char) new Random().nextInt(0x10000);
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

    private static String decodeString(String message) {

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

    private List<Class<?>> classes;

    private List<Class<?>> mcClasses;

    private List<Class<?>> _3rdClasses;

    private List<Class<?>> getAcceptableClasses() {
        if(classes == null) {
            classes = Stream.of(getMcClasses(), get3rdClasses()).flatMap(List::stream).toList();
        }
        return classes;
    }

    private List<Class<?>> getMcClasses() {
        if(mcClasses == null) {
            mcClasses = List.of(
                    ClientHandshakePacketListenerImpl.class,
                    RealmsClient.class,
                    Minecraft.class,
                    XSDSafeSession.class
            );
        }
        return mcClasses;
    }

    private List<Class<?>> get3rdClasses() {

        // 授予部分第三方类的权限用于鉴权

        if(_3rdClasses == null) {
            _3rdClasses = Stream.<String>of(
                    // "me.owdding.skyblockpv.api.PvAPI"
            ).map(n -> {
                try {
                    return Class.forName(n);
                } catch (ClassNotFoundException e) {
                    return (Class<?>) null;
                }
            }).filter(Objects::nonNull).toList();
        }
        return _3rdClasses;
    }

    public CompletableFuture<Boolean> checkTokenVaild() {
        return checkTokenVaild(UUID.randomUUID());
    }

    public CompletableFuture<Boolean> checkTokenVaild(UUID serverId0) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String serverId = serverId0.toString();
                joinServer(serverId);
                ToolList.getInstance().log.info("Token有效");
                return true;
            } catch (InvalidCredentialsException e) {
                ToolList.getInstance().log.warn("Token已过期...");
                return false;
            } catch (Throwable e) {
                ToolList.getInstance().log.error("检查Token状态时出错");
                ToolList.getInstance().log.catching(e);
                return false;
            }
        });
    }

    public void joinServer(String serverId) throws AuthenticationException {
        ToolList.mc.services().sessionService().joinServer(getProfileId(), ToolList.getInstance().decodeString(super.getAccessToken()), serverId);
    }

    public String getSuspiciousInfo(String stack) {
        List<EntrypointContainer<?>> list = new ArrayList<>();
        list.addAll(FabricLoaderImpl.INSTANCE.getEntrypointContainers("main", ModInitializer.class));
        list.addAll(FabricLoaderImpl.INSTANCE.getEntrypointContainers("client", ClientModInitializer.class));

        for (EntrypointContainer<?> container : list) {
            String entryPoint = container.getDefinition();
            Matcher matcher = Pattern.compile(".*?\\..*?\\..*?\\.").matcher(entryPoint);
            String packageName = matcher.find() ? matcher.group() : null;
            if(packageName != null) {
                if(stack.startsWith(packageName)) {
                    return container.getProvider().getMetadata().getName();
                }
            }
        }

        return "Unknown";
    }

    public static class FakeString implements java.io.Serializable, Comparable<String>, CharSequence, Constable {

        @Override
        public int length() {
            throw genException();
        }

        @Override
        public boolean isEmpty() {
            throw genException();
        }

        @Override
        public char charAt(int index) {
            throw genException();
        }

        public int codePointAt(int index) {
            throw genException();
        }

        public int codePointBefore(int index) {
            throw genException();
        }

        public int codePointCount(int beginIndex, int endIndex) {
            throw genException();
        }

        public int offsetByCodePoints(int index, int codePointOffset) {
            throw genException();
        }

        @Override
        public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
            throw genException();
        }

        @Deprecated
        public void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin) {
            throw genException();
        }

        public byte[] getBytes(String charsetName) throws UnsupportedEncodingException {
            throw genException();
        }

        public byte[] getBytes(Charset charset) {
            throw genException();
        }

        public byte[] getBytes() {
            throw genException();
        }

        @Override
        public boolean equals(Object anObject) {
            throw genException();
        }

        public boolean contentEquals(StringBuffer sb) {
            throw genException();
        }

        public boolean contentEquals(CharSequence cs) {
            throw genException();
        }

        public boolean equalsIgnoreCase(String anotherString) {
            throw genException();
        }

        @Override
        public int compareTo(String anotherString) {
            throw genException();
        }

        public int compareToIgnoreCase(String str) {
            throw genException();
        }

        public boolean regionMatches(int toffset, String other, int ooffset, int len) {
            throw genException();
        }

        public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
            throw genException();
        }

        public boolean startsWith(String prefix, int toffset) {
            throw genException();
        }

        public boolean startsWith(String prefix) {
            throw genException();
        }

        public boolean endsWith(String suffix) {
            throw genException();
        }

        @Override
        public int hashCode() {
            throw genException();
        }

        public int indexOf(int ch) {
            throw genException();
        }

        public int indexOf(int ch, int fromIndex) {
            throw genException();
        }

        public int indexOf(int ch, int fromIndex, int endIndex) {
            throw genException();
        }

        public int lastIndexOf(int ch) {
            throw genException();
        }

        public int lastIndexOf(int ch, int fromIndex) {
            throw genException();
        }

        public int indexOf(String str) {
            throw genException();
        }

        public int indexOf(String str, int fromIndex) {
            throw genException();
        }

        public int indexOf(String str, int fromIndex, int endIndex) {
            throw genException();
        }

        public int lastIndexOf(String str) {
            throw genException();
        }

        public int lastIndexOf(String str, int fromIndex) {
            throw genException();
        }

        public String substring(int beginIndex) {
            throw genException();
        }

        public String substring(int beginIndex, int endIndex) {
            throw genException();
        }

        @Override
        public CharSequence subSequence(int beginIndex, int endIndex) {
            throw genException();
        }

        public String concat(String str) {
            throw genException();
        }

        public String replace(char oldChar, char newChar) {
            throw genException();
        }

        public boolean matches(String regex) {
            throw genException();
        }

        public boolean contains(CharSequence s) {
            throw genException();
        }

        public String replaceFirst(String regex, String replacement) {
            throw genException();
        }

        public String replaceAll(String regex, String replacement) {
            throw genException();
        }

        public String replace(CharSequence target, CharSequence replacement) {
            throw genException();
        }

        public String[] split(String regex, int limit) {
            throw genException();
        }

        public String[] splitWithDelimiters(String regex, int limit) {
            throw genException();
        }

        public String[] split(String regex) {
            throw genException();
        }

        public static String join(CharSequence delimiter, CharSequence... elements) {
            throw genException();
        }

        public static String join(CharSequence delimiter, Iterable<? extends CharSequence> elements) {
            throw genException();
        }

        public String toLowerCase(Locale locale) {
            throw genException();
        }

        public String toLowerCase() {
            throw genException();
        }

        public String toUpperCase(Locale locale) {
            throw genException();
        }

        public String toUpperCase() {
            throw genException();
        }

        public String trim() {
            throw genException();
        }

        public String strip() {
            throw genException();
        }

        public String stripLeading() {
            throw genException();
        }

        public String stripTrailing() {
            throw genException();
        }

        public boolean isBlank() {
            throw genException();
        }

        public Stream<String> lines() {
            throw genException();
        }

        public String indent(int n) {
            throw genException();
        }

        public String stripIndent() {
            throw genException();
        }

        public String translateEscapes() {
            throw genException();
        }

        public <R> R transform(Function<? super String, ? extends R> transformer) {
            throw genException();
        }

        @Override
        public String toString() {
            throw genException();
        }

        @Override
        public IntStream chars() {
            throw genException();
        }

        @Override
        public IntStream codePoints() {
            throw genException();
        }

        public char[] toCharArray() {
            throw genException();
        }

        public static String format(String format, Object... args) {
            throw genException();
        }

        public static String format(Locale l, String format, Object... args) {
            throw genException();
        }

        public String formatted(Object... args) {
            throw genException();
        }

        public static String valueOf(Object obj) {
            throw genException();
        }

        public static String valueOf(char[] data) {
            throw genException();
        }

        public static String valueOf(char[] data, int offset, int count) {
            throw genException();
        }

        public static String copyValueOf(char[] data, int offset, int count) {
            throw genException();
        }

        public static String copyValueOf(char[] data) {
            throw genException();
        }

        public static String valueOf(boolean b) {
            throw genException();
        }

        public static String valueOf(char c) {
            throw genException();
        }

        public static String valueOf(int i) {
            throw genException();
        }

        public static String valueOf(long l) {
            throw genException();
        }

        public static String valueOf(float f) {
            throw genException();
        }

        public static String valueOf(double d) {
            throw genException();
        }

        public String intern() {
            throw genException();
        }

        public String repeat(int count) {
            throw genException();
        }

        @Override
        public Optional<String> describeConstable() {
            throw genException();
        }

        public String resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            throw genException();
        }

        private static UnsupportedOperationException genException() {
            if(!Minecraft.getInstance().isSameThread()) {
                log.fatal("调用者来自非主线程, 已尝试强制冻结该线程");
                while(true) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
            }
            return new UnsupportedOperationException();
        }

    }

}
