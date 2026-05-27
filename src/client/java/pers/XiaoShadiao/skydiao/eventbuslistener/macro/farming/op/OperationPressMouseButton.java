package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op;

import com.google.gson.JsonObject;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class OperationPressMouseButton implements IOperation<JsonObject> {

    public boolean isLeft;
    public boolean isRight;

    @Override
    public void op() {
        if (isLeft) {
            InputSimulator.pressLeftClick();
        }
        if (isRight) {
            InputSimulator.pressRightClick();
        }
    }

    @Override
    public JsonObject saveAsJson() {
        JsonObject json = new JsonObject();
        json.addProperty("isLeft", isLeft);
        json.addProperty("isRight", isRight);
        return json;
    }

    @Override
    public void readFromJson(JsonObject json) {
        isLeft = json.get("isLeft").getAsBoolean();
        isRight = json.get("isRight").getAsBoolean();
    }

    @Override
    public String toChatString() {
        return "[LC: " + isLeft + ", RC: " + isRight + "]";
    }

    @Override
    public IOperation<JsonObject> clone() {
        try {
            return (IOperation<JsonObject>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

}
