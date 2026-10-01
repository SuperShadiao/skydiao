package pers.XiaoShadiao.skydiao.utils.pathfinderv2;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PathNodes implements Comparable<PathNodes> {

    private final PathFinderV2State state;

    private final List<PathNode> path;
    private final double cost;

    public PathNodes(List<PathNode> path, double cost, PathFinderV2State state) {
        this.path = List.copyOf(path);
        this.cost = cost;
        this.state = state;
    }

    public List<PathNode> getPath() {
        return path;
    }

    public double getCost() {
        return cost;
    }

    @Override
    public int compareTo(@NotNull PathNodes o) {
        return Double.compare(cost, o.cost);
    }

    public PathFinderV2State getState() {
        return state;
    }

}