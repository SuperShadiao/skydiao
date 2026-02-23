package pers.XiaoShadiao.skydiao.irc;

import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class ChatClientManager {

    private static ChatClient chatClient;
    public static ChatClient getChatClient() {
        if(chatClient == null || !chatClient.isAlive()) refreshChatClient();
        return chatClient;
    }

    public static void refreshChatClient() {
        refreshChatClient(true);
    }

    static void refreshChatClient(boolean flag) {
        if(flag) ChatClient.retryCount = 3;
        chatClient = new ChatClient();
        chatClient.start();
    }

    public static boolean serverAvailable() {
        return ChatClient.chatServerAvailable;
    }

    public static void trySendOrWarning(ChatPacket p) {
        if(ChatClientManager.serverAvailable()) ChatClientManager.getChatClient().sender.send(p); else ToolList.printChatMessage(Component.literal("§a[小沙雕] §c当前服务器暂时不可用, 请稍后再试! 若仍然没有解决, 请联系小沙雕!"));
    }

}
