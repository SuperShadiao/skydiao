package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class OperationPressMove implements IOperation<JsonArray> {

    public Move[] moves;

    @Override
    public void op() {
        for (Move move : moves) {
            move.execute();
        }
    }

    public OperationPressMove setMoves(Move... moves) {
        this.moves = moves;
        return this;
    }

    @Override
    public JsonArray saveAsJson() {
        JsonArray ja = new JsonArray();
        Arrays.stream(moves).map(Enum::name).forEach(ja::add);
        return ja;
    }

    @Override
    public void readFromJson(JsonArray json) {
        moves = StreamSupport.stream(json.spliterator(), false).map(JsonElement::getAsString).map(Move::valueOf).toArray(Move[]::new);
    }

    @Override
    public String toChatString() {
        return Arrays.toString(moves);
    }

    @Override
    public OperationPressMove clone() {
        try {
            OperationPressMove clone = (OperationPressMove) super.clone();
            if (this.moves != null) {
                clone.moves = this.moves.clone();
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public enum Move {
        FORWARD(InputSimulator::setForward),
        BACKWARD(InputSimulator::setBackward),
        LEFT(InputSimulator::setLeft),
        RIGHT(InputSimulator::setRight);

        private final Consumer<Boolean> moveOp;

        Move(Consumer<Boolean> move) {
            moveOp = move;
        }

        public void execute() {
            moveOp.accept(true);
        }

    }

}
