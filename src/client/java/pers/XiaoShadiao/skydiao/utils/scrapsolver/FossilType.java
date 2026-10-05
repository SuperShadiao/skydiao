package pers.XiaoShadiao.skydiao.utils.scrapsolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/mining/fossilexcavator/solver

public enum FossilType {
    TUSK(
            "Tusk", 8, "12.5%",
            new FossilShape(Arrays.asList(
                    new FossilTile(0, 2),
                    new FossilTile(0, 3),
                    new FossilTile(0, 4),
                    new FossilTile(1, 1),
                    new FossilTile(2, 0),
                    new FossilTile(3, 1),
                    new FossilTile(3, 3),
                    new FossilTile(4, 2)
            )),
            Arrays.asList(FossilMutation.values())
    ),
    WEBBED(
            "Webbed", 10, "10%",
            new FossilShape(Arrays.asList(
                    new FossilTile(0, 2),
                    new FossilTile(1, 1),
                    new FossilTile(2, 0),
                    new FossilTile(3, 0),
                    new FossilTile(3, 1),
                    new FossilTile(3, 2),
                    new FossilTile(3, 3),
                    new FossilTile(4, 0),
                    new FossilTile(5, 1),
                    new FossilTile(6, 2)
            )),
            Arrays.asList(FossilMutation.ROTATE_0, FossilMutation.FLIP_ROTATE_0)
    ),
    CLUB(
            "Club", 11, "9.1%",
            new FossilShape(Arrays.asList(
                    new FossilTile(0, 2),
                    new FossilTile(0, 3),
                    new FossilTile(1, 2),
                    new FossilTile(1, 3),
                    new FossilTile(2, 1),
                    new FossilTile(3, 0),
                    new FossilTile(4, 0),
                    new FossilTile(5, 0),
                    new FossilTile(6, 0),
                    new FossilTile(6, 2),
                    new FossilTile(7, 1)
            )),
            Arrays.asList(
                    FossilMutation.ROTATE_0,
                    FossilMutation.ROTATE_180,
                    FossilMutation.FLIP_ROTATE_0,
                    FossilMutation.FLIP_ROTATE_180
            )
    ),
    SPINE(
            "Spine", 12, "8.3%",
            new FossilShape(Arrays.asList(
                    new FossilTile(0, 2),
                    new FossilTile(1, 1),
                    new FossilTile(1, 2),
                    new FossilTile(2, 0),
                    new FossilTile(2, 1),
                    new FossilTile(2, 2),
                    new FossilTile(3, 0),
                    new FossilTile(3, 1),
                    new FossilTile(3, 2),
                    new FossilTile(4, 1),
                    new FossilTile(4, 2),
                    new FossilTile(5, 2)
            )),
            FossilMutation.ONLY_ROTATION
    ),
    CLAW(
            "Claw", 13, "7.7%",
            new FossilShape(Arrays.asList(
                    new FossilTile(0, 3),
                    new FossilTile(1, 2),
                    new FossilTile(1, 4),
                    new FossilTile(2, 1),
                    new FossilTile(2, 3),
                    new FossilTile(3, 1),
                    new FossilTile(3, 2),
                    new FossilTile(3, 4),
                    new FossilTile(4, 0),
                    new FossilTile(4, 1),
                    new FossilTile(4, 2),
                    new FossilTile(4, 3),
                    new FossilTile(5, 1)
            )),
            Arrays.asList(FossilMutation.values())
    ),
    FOOTPRINT(
            "Footprint", 13, "7.7%",
            new FossilShape(Arrays.asList(
                    new FossilTile(0, 2),
                    new FossilTile(1, 1),
                    new FossilTile(1, 2),
                    new FossilTile(1, 3),
                    new FossilTile(2, 1),
                    new FossilTile(2, 2),
                    new FossilTile(2, 3),
                    new FossilTile(3, 0),
                    new FossilTile(3, 2),
                    new FossilTile(3, 4),
                    new FossilTile(4, 0),
                    new FossilTile(4, 2),
                    new FossilTile(4, 4)
            )),
            FossilMutation.ONLY_ROTATION
    ),
    HELIX(
            "Helix", 14, "7.1%",
            new FossilShape(Arrays.asList(
                    new FossilTile(0, 0),
                    new FossilTile(0, 1),
                    new FossilTile(0, 2),
                    new FossilTile(0, 4),
                    new FossilTile(1, 0),
                    new FossilTile(1, 2),
                    new FossilTile(1, 4),
                    new FossilTile(2, 0),
                    new FossilTile(2, 4),
                    new FossilTile(3, 0),
                    new FossilTile(3, 1),
                    new FossilTile(3, 2),
                    new FossilTile(3, 3),
                    new FossilTile(3, 4)
            )),
            Arrays.asList(FossilMutation.values())
    ),
    UGLY(
            "Ugly", 16, "6.2%",
            new FossilShape(Arrays.asList(
                    new FossilTile(0, 1),
                    new FossilTile(1, 0),
                    new FossilTile(1, 1),
                    new FossilTile(1, 2),
                    new FossilTile(2, 0),
                    new FossilTile(2, 1),
                    new FossilTile(2, 2),
                    new FossilTile(2, 3),
                    new FossilTile(3, 0),
                    new FossilTile(3, 1),
                    new FossilTile(3, 2),
                    new FossilTile(3, 3),
                    new FossilTile(4, 0),
                    new FossilTile(4, 1),
                    new FossilTile(4, 2),
                    new FossilTile(5, 1)
            )),
            FossilMutation.ONLY_ROTATION
    );

    private final String displayName;
    private final int totalTiles;
    private final String firstPercentage;
    private final FossilShape fossilShape;
    private final List<FossilMutation> possibleMutations;

    FossilType(String displayName, int totalTiles, String firstPercentage,
               FossilShape fossilShape, List<FossilMutation> possibleMutations) {
        this.displayName = displayName;
        this.totalTiles = totalTiles;
        this.firstPercentage = firstPercentage;
        this.fossilShape = fossilShape;
        this.possibleMutations = possibleMutations;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTotalTiles() {
        return totalTiles;
    }

    public String getFirstPercentage() {
        return firstPercentage;
    }

    public FossilShape getFossilShape() {
        return fossilShape;
    }

    public List<FossilMutation> getPossibleMutations() {
        return possibleMutations;
    }

    public static List<FossilType> getByPercentage(String percentage) {
        List<FossilType> result = new ArrayList<>();
        for (FossilType type : values()) {
            if (type.firstPercentage.equals(percentage)) {
                result.add(type);
            }
        }
        return result;
    }
}
