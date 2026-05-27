package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op;

import com.google.gson.JsonPrimitive;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class OperationSendCommand implements IOperation<JsonPrimitive> {

    public String command;

    @Override
    public void op() {
        ToolList.addThreadedTask(() -> {
            Thread.sleep(700 + ToolList.getInstance().random.nextInt(600));
            ToolList.sendChatMessage(command);
            return null;
        });
    }

    @Override
    public JsonPrimitive saveAsJson() {
        return new JsonPrimitive(command);
    }

    @Override
    public void readFromJson(JsonPrimitive json) {
        command = json.getAsString();
    }

    @Override
    public String toChatString() {
        return "Command: " + command;
    }

    @Override
    public OperationSendCommand clone() {
        try {
            return (OperationSendCommand) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

}
