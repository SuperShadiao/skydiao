package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op;

import com.google.gson.JsonElement;

public interface IOperation<JsonType extends JsonElement> extends Cloneable {

    public void op();
    public JsonType saveAsJson();

    // default void readFromJson0()

    public void readFromJson(JsonType json);

    public String toChatString();

    public IOperation<JsonType> clone();

}
