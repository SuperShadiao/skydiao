package pers.XiaoShadiao.skydiao.utils.scrapsolver;

// https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/mining/fossilexcavator/solver

public record FossilTile(int x, int y) {

    public FossilTile(int slotIndex) {
        this(slotIndex % 9, slotIndex / 9);
    }

    public int toSlotIndex() {
        return x + y * 9;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FossilTile)) return false;
        FossilTile that = (FossilTile) o;
        return x == that.x && y == that.y;
    }

}
