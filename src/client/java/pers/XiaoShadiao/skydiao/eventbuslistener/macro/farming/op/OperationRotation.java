package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class OperationRotation implements IOperation<JsonObject> {

    public float yaw;
    public float pitch;

    @Override
    public void op() {
        InputSimulator.setPlayerYaw(yaw);
        InputSimulator.setPlayerPitch(pitch);
    }

    @Override
    public JsonObject saveAsJson() {
        JsonObject jo = new JsonObject();
        jo.addProperty("yaw", yaw);
        jo.addProperty("pitch", pitch);
        return jo;
    }

    @Override
    public void readFromJson(JsonObject json) {
        yaw = json.get("yaw").getAsFloat();
        pitch = json.get("pitch").getAsFloat();
    }

    @Override
    public String toChatString() {
        return "[Yaw: " + yaw + ", Pitch: " + pitch + "]";
    }

    @Override
    public OperationRotation clone() {
        try {
            return (OperationRotation) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

}
