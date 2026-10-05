package pers.XiaoShadiao.skydiao.utils.scrapsolver;

import java.util.List;
import java.util.stream.Collectors;

// https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/mining/fossilexcavator/solver

public class FossilShape {
    private final List<FossilTile> tiles;

    public FossilShape(List<FossilTile> tiles) {
        this.tiles = tiles;
    }

    public List<FossilTile> getTiles() {
        return tiles;
    }

    public int width() {
        return tiles.stream().mapToInt(FossilTile::x).max().orElse(0)
                - tiles.stream().mapToInt(FossilTile::x).min().orElse(0);
    }

    public int height() {
        return tiles.stream().mapToInt(FossilTile::y).max().orElse(0)
                - tiles.stream().mapToInt(FossilTile::y).min().orElse(0);
    }

    public FossilShape moveTo(int x, int y) {
        return new FossilShape(tiles.stream()
                .map(t -> new FossilTile(t.x() + x, t.y() + y))
                .collect(Collectors.toList()));
    }

    public FossilShape rotate(int degree) {
        int w = this.width();
        int h = this.height();
        switch (degree) {
            case 90:
                return new FossilShape(tiles.stream()
                        .map(t -> new FossilTile(t.y(), w - t.x()))
                        .collect(Collectors.toList()));
            case 180:
                return new FossilShape(tiles.stream()
                        .map(t -> new FossilTile(w - t.x(), h - t.y()))
                        .collect(Collectors.toList()));
            case 270:
                return new FossilShape(tiles.stream()
                        .map(t -> new FossilTile(h - t.y(), t.x()))
                        .collect(Collectors.toList()));
            default:
                return this;
        }
    }

    public FossilShape flipShape() {
        int h = this.height();
        return new FossilShape(tiles.stream()
                .map(t -> new FossilTile(t.x(), h - t.y()))
                .collect(Collectors.toList()));
    }
}
