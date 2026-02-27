package pers.XiaoShadiao.skydiao.utils.mircosoftaccount;

import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.UUID;

public class XSDSafeSession extends User {

    private boolean ticket = false;

    public XSDSafeSession(String name, UUID uuid, String accessToken) {
        super(name, uuid, ToolList.getInstance().encodeString(accessToken), ToolList.mc.getUser().getXuid(), ToolList.mc.getUser().getClientId());
    }

    public XSDSafeSession(User parent) {
        super(parent.getName(), parent.getProfileId(), ToolList.getInstance().encodeString(parent.getAccessToken()), parent.getXuid(), parent.getClientId());
    }

    private Object[] checkStack() {
        StackTraceElement[] ste = new Throwable().getStackTrace();
        boolean flag = false;
        int deepth = 2;
        String stack = ste[deepth].getClassName();

        boolean isFullySafe = ClientHandshakePacketListenerImpl.class.getName().equals(stack) || RealmsClient.class.getName().equals(stack);
        if (isFullySafe) flag = true;
        if (stack.startsWith("gg.essential")) {
            return new Object[] {false, stack};
        }

        if (!flag) {
            new Throwable().printStackTrace();
        }

        return new Object[] {flag, stack};
    }

    @Override
    public @NotNull String getAccessToken() {
        Object[] objects = checkStack();
        boolean allow = (boolean) objects[0];
        String stack = (String) objects[1];

        if (allow || ticket) {
            ticket = false;
            return ToolList.getInstance().decodeString(super.getAccessToken());
        }

        throw new RuntimeException(new IllegalAccessException("getAccessToken() is not allowed in " + stack));
    }

    @Override
    public @NotNull String getSessionId() {
        Object[] objects = checkStack();
        boolean allow = (boolean) objects[0];
        String stack = (String) objects[1];

        if (allow || ticket) {
            ticket = false;
            // public String getSessionId() {
            //		return "token:" + this.accessToken + ":" + UndashedUuid.toString(this.uuid);
            //	}
            return "token:" + ToolList.getInstance().decodeString(super.getAccessToken()) + ":" + UndashedUuid.toString(this.getProfileId());
        }

        throw new RuntimeException(new IllegalAccessException("getSessionId() is not allowed in " + stack));
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

}
