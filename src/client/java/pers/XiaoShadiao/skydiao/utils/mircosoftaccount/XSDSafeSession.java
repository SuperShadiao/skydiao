package pers.XiaoShadiao.skydiao.utils.mircosoftaccount;

import com.mojang.util.UndashedUuid;
import net.minecraft.client.User;
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
        boolean flag = false, isEssential = false;
        int deepth = 2;
        String stack = ste[deepth].getClassName();

        boolean isFullySafe = "net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl".equals(stack) || "com.mojang.realmsclient.client.RealmsClient".equals(stack);
        if (isFullySafe || "gg.skytils.skytilsws.client.PacketHandler".equals(stack)) flag = true;
        if (stack.startsWith("gg.essential.util.HelpersKt") || stack.startsWith("gg.essential.handlers.ReAuthChecker") || stack.startsWith("gg.essential.handlers.NetworkHook") || stack.startsWith("gg.essential.network.connectionmanager.ConnectionManager") || stack.startsWith("gg.essential.universal.UChat") || stack.startsWith("gg.essential.network.cosmetics.cape.MojangCapeApi")) {
            flag = true;
            isEssential = true;
        }

        try {
            if (!flag) {
                new Throwable().printStackTrace();
            } else if (!isEssential) {
            }
        } catch (Exception e) {
        }

        return new Object[]{flag, stack};
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
            return "token:" + ToolList.getInstance().decodeString(super.getAccessToken()) + ":" + this.getProfileId();
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
