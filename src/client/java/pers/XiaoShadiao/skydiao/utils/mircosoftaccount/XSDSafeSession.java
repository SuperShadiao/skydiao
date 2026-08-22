package pers.XiaoShadiao.skydiao.utils.mircosoftaccount;

import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.util.UndashedUuid;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class XSDSafeSession extends User {

    private static final List<UUID> requestedBypassList = List.of(
            // UUID.fromString("3f448a12-a2b3-46ef-9a46-ca145b2c9550")
    );

    private static final Logger log = LogManager.getLogger();
    private boolean ticket = false;

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

        if (allow || ticket) {
            printGetterStack("getAccessToken", stack);
            ticket = false;
            return ToolList.getInstance().decodeString(super.getAccessToken());
        }

        throwException("getAccessToken", stack);
        return "";
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

        if (allow || ticket) {
            // public String getSessionId() {
            //		return "token:" + this.accessToken + ":" + UndashedUuid.toString(this.uuid);
            //	}
            printGetterStack("getSessionId", stack);
            ticket = false;
            return "token:" + ToolList.getInstance().decodeString(super.getAccessToken()) + ":" + UndashedUuid.toString(this.getProfileId());
        }


        throwException("getSessionId", stack);
        return "";
    }

    public void openTicket() {
        ticket = true;
    }

    public boolean equals(Object session) {
        if (session instanceof User user) {
            return user.getProfileId().equals(this.getProfileId()) && user.getName().equals(this.getName());
        }
        return false;
    }

    private void printGetterStack(String str, String stack) {
        log.warn("椎栈" + stack + "在刚才调用了" + str + "()");
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e椎栈" + stack + "在刚才通过" + str + "()获取了你的Token"));
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e可能来自于Mod: §6" + getSuspiciousInfo(stack)));
        if(ticket) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e该椎栈通过Ticket成功获取了Token, 但如果你不认识该mod, 请检查你的mod列表"));
    }

    private void throwException(String str, String stack) {
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c未经授权的椎栈" + stack + "尝试通过" + str + "()获取你的Token, 已进行拦截"));
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c可能来自于Mod: §6" + getSuspiciousInfo(stack)));
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c如果你不认识该Mod, 可能为恶意模组, 请立即删除"));
        if(!Minecraft.getInstance().isSameThread()) {
            new Throwable().printStackTrace();
            log.fatal("调用者来自非主线程, 已尝试强制冻结该线程");
            while(true) {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    continue;
                }
            }
        }
        throw new IllegalAccessError(str + "() is not allowed in " + stack);
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
            _3rdClasses = Stream.of(
                    "me.owdding.skyblockpv.api.PvAPI"
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
                ToolList.mc.services().sessionService().joinServer(getProfileId(), ToolList.getInstance().decodeString(super.getAccessToken()), serverId);
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

}
