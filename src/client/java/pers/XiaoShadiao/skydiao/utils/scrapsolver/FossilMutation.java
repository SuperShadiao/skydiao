package pers.XiaoShadiao.skydiao.utils.scrapsolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

// https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/mining/fossilexcavator/solver

public enum FossilMutation {
    ROTATE_0(shape -> shape),
    ROTATE_90(shape -> shape.rotate(90)),
    ROTATE_180(shape -> shape.rotate(180)),
    ROTATE_270(shape -> shape.rotate(270)),
    FLIP_ROTATE_0(shape -> shape.flipShape()),
    FLIP_ROTATE_90(shape -> shape.flipShape().rotate(90)),
    FLIP_ROTATE_180(shape -> shape.flipShape().rotate(180)),
    FLIP_ROTATE_270(shape -> shape.flipShape().rotate(270));

    private final Function<FossilShape, FossilShape> modification;

    FossilMutation(Function<FossilShape, FossilShape> modification) {
        this.modification = modification;
    }

    public Function<FossilShape, FossilShape> getModification() {
        return modification;
    }

    public static final List<FossilMutation> ONLY_ROTATION = Collections.unmodifiableList(
            Arrays.asList(ROTATE_0, ROTATE_90, ROTATE_180, ROTATE_270)
    );
}
