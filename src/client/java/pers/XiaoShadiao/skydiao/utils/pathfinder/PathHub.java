package pers.XiaoShadiao.skydiao.utils.pathfinder;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;

public class PathHub {
    private BlockPos loc;
    private ArrayList<BlockPos> pathway;
    private double sqDist;
    private double dist;
    private double currentCost;
    private double maxCost;
    private BlockPos end;
    private double manhattanDist;

    public PathHub(BlockPos loc, PathHub parentPathHub, ArrayList<BlockPos> pathway, double sqDist, double currentCost, double maxCost, BlockPos end) {
        this.loc = loc;
        this.pathway = pathway;
        this.sqDist = sqDist;
        this.dist = Math.sqrt(sqDist);
        this.currentCost = currentCost;
        this.maxCost = maxCost;
        this.end = end;

//        this.manhattanDist = Math.abs(getLoc().getX() - getEnd().getX()) + Math.abs(getLoc().getY() - getEnd().getY()) + Math.abs(getLoc().getZ() - getEnd().getZ());
        calcManhattanDist();
    }

    private void calcManhattanDist() {
        this.manhattanDist = getLoc().distManhattan(getEnd());
    }

    public BlockPos getLoc() {
        return this.loc;
    }

    public ArrayList<BlockPos> getPathway() {
        return this.pathway;
    }
//
//    public double getDist() {
//        double sqrt = Math.sqrt(sqDist);
//        if(!Double.isFinite(sqrt)) throw new NMSLException("Unknown value: " + sqDist);
//        return sqrt;
//    }

    public double getSqDist() {
        return this.sqDist;
    }

    public BlockPos getEnd() {
        return this.end;
    }

    public double getDist() {
        return this.dist;
    }

    public double getCurrentCost() {
        return this.currentCost;
    }

    public double getManhattanDist() {
        return this.manhattanDist;
    }

    public void setLoc(BlockPos loc) {
        this.loc = loc;
        calcManhattanDist();
    }

    public void setParentPathHub(PathHub parentPathHub) {
    }

    public void setPathway(ArrayList<BlockPos> pathway) {
        this.pathway = pathway;
    }

    public void setSqDist(double sqDist) {
        this.sqDist = sqDist;
        this.dist = Math.sqrt(sqDist);
    }

    public void setCurrentCost(double currentCost) {
        this.currentCost = currentCost;
    }

    public double getMaxCost() {
        return this.maxCost;
    }

    public void setMaxCost(double maxCost) {
        this.maxCost = maxCost;
    }
}

