package pers.XiaoShadiao.skydiao.utils.pathfinder;

import org.jetbrains.annotations.NotNull;

public record PathBlockInfo(boolean isValid, double placeCost, boolean needBreak) {
    @Override
    public @NotNull String toString() {
        return "PathBlockInfo{" +
                "isValid=" + isValid +
                ", placeCost=" + placeCost +
                ", needBreak=" + needBreak +
                '}';
    }
}
