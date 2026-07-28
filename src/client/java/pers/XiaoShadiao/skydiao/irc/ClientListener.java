package pers.XiaoShadiao.skydiao.irc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.BufferOverflowException;
import java.nio.charset.StandardCharsets;

public class ClientListener extends ChatClient {

    private boolean announcedAuthors;

    private final InputStream is;

    public ClientListener(InputStream is) {
        this.is = is;
    }

    public void announceAuthors() {
        if(!announcedAuthors) {
            announcedAuthors = true;
            try {
                ToolList.printChatMessage(
                        Component.literal("§a[小沙雕] §eXSDChat server file by ")
                                .append(Component.literal("§6XiaoShadiao").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("§b点击访问他的网站!"))).withClickEvent(new ClickEvent.OpenUrl(URI.create("https://github.com/SuperShadiao/hypixelhelper")))))
                                .append("§e, server by ")
                                .append(Component.literal("§6ABCOA§e!").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("§b点击访问他的网站!"))).withClickEvent(new ClickEvent.OpenUrl(URI.create("https://github.com/ABCOA/Legacy-SoarClient"))))

                ));
            } catch(Exception ignored) {}
        }
    }

    public static final ClientReceiveHandler handler = new ClientReceiveHandler();
    @Override
    public void run() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try {
            int i;
            boolean startRecordFlag = false;

            while((i = is.read()) != -1) {

                try {
                    buffer.write(i);
                    byte[] bytes = buffer.toByteArray();
                    for(int j = 0; j < Math.min(4, bytes.length); j++) {
                        if((startRecordFlag ? END : HEADER)[j] != bytes[j]) {
                            data.write(bytes);
                            buffer.reset();
                            break;
                        }
                    }
                    if(bytes.length == (startRecordFlag ? END : HEADER).length) {
                        if(startRecordFlag) {
                            try {
                                ChatPacket packet = new ChatPacket();
                                String s = new String(data.toByteArray(), StandardCharsets.UTF_8);
                                s = s.substring(s.indexOf('{'));
                                JsonObject jo = new JsonParser().parse(s).getAsJsonObject();
                                packet.raw = jo;
                                packet.setFromJson(jo);
                                // {"message":"SB","packetType":"chat","sender":"5j_XiaoShadiao","senderUUID":"Player0"}
                                // System.out.println("Received packet " + jo + " from " + socket.getInetAddress());
                                // 工具列表.mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("§a[XSDChat] §" + packet.sender + "§7: §f" + packet.message));
                                // 工具列表.mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("§a[XSDChat] 请使用/xsdc message聊天!"));

                                switch(String.valueOf(packet.packetType)) {
                                    case "heartbeat":
                                    case "lps":
                                        break;
                                    default:
                                        String json = packet.getJson().toString();
                                        if(json.length() < 500) {
                                            log.info("Received packet: " + json);
                                        } else {
                                            log.info("Received packet " + packet.packetType + " (" + json.length() + ")");
                                        }
                                        break;
                                }

                                handler.handle(packet, this);
                            } catch (Exception e) {
                                if(ToolList.getInstance().isXiaoShadiao()) {
                                    System.out.println("BAD PACKET: " + new String(data.toByteArray(), StandardCharsets.UTF_8));
                                    e.printStackTrace();
                                }
                                startRecordFlag = false;
                                buffer.reset();
                                data.reset();
                                continue;
                            }
                        } else {
                        }
                        startRecordFlag = !startRecordFlag;
                        buffer.reset();
                        data.reset();
                    }
                } catch (BufferOverflowException e) {
                    buffer.reset();
                    data.reset();
                    startRecordFlag = false;
                }

            }

            throw new RuntimeException("InputStream closed");
        } catch (Throwable e) {
            log.warn("Client Listener closed with data");
            StringBuilder bytes = new StringBuilder("data: ");
            for (byte b : data.toByteArray()) {
                String bt = Integer.toHexString(b);
                if(bt.length() == 1) bytes.append("0");
                bytes.append(bt).append(" ");
            }
            log.warn(bytes.toString());

            throw new RuntimeException(e);
        }
    }
}
