package pers.XiaoShadiao.skydiao.irc;

import com.google.gson.JsonObject;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.nio.charset.StandardCharsets;

public class ChatPacket {

    public JsonObject raw;

    public String message;
    public String packetType;
    public String sender;
    public String senderUUID;
    public String senderRankName;

    public String appendedSenderName;

    public byte[] getBuffer() {
        if(raw == null) {
           getJson();
        }
        return raw.toString().getBytes(StandardCharsets.UTF_8);
    }

    public JsonObject getJson() {
        raw = new JsonObject();
        raw.addProperty("message", message);
        raw.addProperty("packetType", packetType);
        raw.addProperty("sender", sender);
        raw.addProperty("senderUUID", senderUUID);
        raw.addProperty("senderRankName", senderRankName);
        return raw;
    }

    public void setFromJson(JsonObject jo) {
        message = jo.get("message").isJsonNull() ? "" : jo.get("message").getAsString();
        packetType = jo.get("packetType").isJsonNull() ? "" : jo.get("packetType").getAsString();
        sender = jo.get("sender").isJsonNull() ? "" : jo.get("sender").getAsString();
        senderUUID = jo.get("senderUUID").isJsonNull() ? "" : jo.get("senderUUID").getAsString();
        senderRankName = jo.get("senderRankName").isJsonNull() ? "" : jo.get("senderRankName").getAsString();
        appendedSenderName = senderRankName + " " + sender;
    }

    public String getRank(boolean addSpace) {
        return (!ToolList.getInstance().stringHasContext(senderRankName) ? "" : (senderRankName + (addSpace ? " " : "")));
    }

    public void initSender() {
        senderUUID = ToolList.mc.getUser().getProfileId().toString().replace("-", "");
        sender = ToolList.mc.getUser().getName();
    }

}
