package pers.XiaoShadiao.skydiao.utils.pathfinderv2;

public enum PathNodeType {
    MOVE(true, false, false),
    UP(false, true, false),
    DOWN(false, false, false),
    FLY(false, false, false),
    AOTV_FLY(false, false, false),
    JUMP1(true, true, true),
    JUMP2(true, true, true),
    JUMP3(true, true, true),
    JUMP1_UP(true, true, true),
    JUMP2_UP(true, true, true),
    ;

    private final boolean isMove;
    private final boolean isJump;
    private final boolean isJumpMove;

    public boolean isMove() {
        return isMove;
    }
    public boolean isJump() {
        return isJump;
    }
    public boolean isJumpMove() {
        return isJumpMove;
    }

    PathNodeType(boolean isMove, boolean isJump, boolean isJumpMove) {
        this.isMove = isJumpMove || isMove;
        this.isJump = isJumpMove || isJump;
        this.isJumpMove = isJumpMove;
    }

}
