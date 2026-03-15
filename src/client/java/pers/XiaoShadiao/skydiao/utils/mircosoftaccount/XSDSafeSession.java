package pers.XiaoShadiao.skydiao.utils.mircosoftaccount;

import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;

public class XSDSafeSession extends User {

    private static long lastPrintTime = 0;
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

        List<Class<?>> acceptClass = List.of(
                ClientHandshakePacketListenerImpl.class,
                RealmsClient.class,
                Minecraft.class,
                XSDSafeSession.class
        );
        Optional<Class<?>> opt = acceptClass.stream().filter(c -> c.getName().equals(stack)).findAny();
        boolean isFullySafe = opt.isPresent();
        if (isFullySafe) flag = true;
        if (stack.startsWith("gg.essential")) {
            return new Object[] {false, stack};
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
            ticket = false;
            printGetterStack("getAccessToken", stack);
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
            ticket = false;
            // public String getSessionId() {
            //		return "token:" + this.accessToken + ":" + UndashedUuid.toString(this.uuid);
            //	}
            printGetterStack("getSessionId", stack);
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
        if(System.currentTimeMillis() - lastPrintTime > 10000) {
            log.warn("椎栈" + stack + "在刚才调用了" + str + "()");
        }
    }

    private void throwException(String str, String stack) {
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
}
