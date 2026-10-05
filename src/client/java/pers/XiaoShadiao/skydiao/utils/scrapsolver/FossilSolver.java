package pers.XiaoShadiao.skydiao.utils.scrapsolver;

import java.util.*;

// https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/mining/fossilexcavator/solver

public class FossilSolver {

    private static class MoveEntry {
        final FossilTile position;
        final double probability;
        final int remaining;

        MoveEntry(FossilTile position, double probability, int remaining) {
            this.position = position;
            this.probability = probability;
            this.remaining = remaining;
        }
    }

    private static final List<MoveEntry> RISKY_STARTING_SEQUENCE = Collections.unmodifiableList(Arrays.asList(
            new MoveEntry(new FossilTile(4, 2), 0.515, 404),
            new MoveEntry(new FossilTile(5, 3), 0.393, 196),
            new MoveEntry(new FossilTile(3, 2), 0.513, 119),
            new MoveEntry(new FossilTile(7, 2), 0.345, 58),
            new MoveEntry(new FossilTile(1, 3), 0.342, 38),
            new MoveEntry(new FossilTile(3, 4), 0.6, 25),
            new MoveEntry(new FossilTile(5, 1), 0.8, 10),
            new MoveEntry(new FossilTile(4, 3), 1.0, 2)
    ));

    private static final List<MoveEntry> SAFE_STARTING_SEQUENCE = Collections.unmodifiableList(Arrays.asList(
            new MoveEntry(new FossilTile(4, 2), 0.515, 404),
            new MoveEntry(new FossilTile(5, 4), 0.413, 196),
            new MoveEntry(new FossilTile(3, 3), 0.461, 115),
            new MoveEntry(new FossilTile(5, 2), 0.387, 62),
            new MoveEntry(new FossilTile(3, 1), 0.342, 38),
            new MoveEntry(new FossilTile(7, 3), 0.48, 25),
            new MoveEntry(new FossilTile(1, 2), 0.846, 13),
            new MoveEntry(new FossilTile(3, 4), 1.0, 2)
    ));

    private static List<MoveEntry> getCurrentSequence() {
        return SAFE_STARTING_SEQUENCE;
    }

    private static boolean isPositionInStartSequence(FossilTile position) {
        for (MoveEntry entry : getCurrentSequence()) {
            if (entry.position.equals(position)) {
                return true;
            }
        }
        return false;
    }

    public static FossilTile findBestTile(Set<Integer> fossilLocations, Set<Integer> dirtLocations) {
        Set<FossilTile> invalidPositions = new HashSet<>();
        for (int i = 0; i <= 53; i++) {
            if (!fossilLocations.contains(i) && !dirtLocations.contains(i)) {
                invalidPositions.add(new FossilTile(i));
            }
        }

        Set<FossilTile> foundPositions = new HashSet<>();
        for (int loc : fossilLocations) {
            foundPositions.add(new FossilTile(loc));
        }

        boolean needsMoveSequence = foundPositions.isEmpty();
        if (needsMoveSequence) {
            for (FossilTile tile : invalidPositions) {
                if (!isPositionInStartSequence(tile)) {
                    needsMoveSequence = false;
                    break;
                }
            }
        }

        if (needsMoveSequence) {
            int movesTaken = invalidPositions.size();
            if (movesTaken >= getCurrentSequence().size()) {
                return null;
            }

            MoveEntry nextMove = getCurrentSequence().get(movesTaken);
            return nextMove.position;
        }

        Map<FossilTile, Integer> possibleClickPositions = new HashMap<>();

        FossilType[] possibleFossilTypes = FossilType.values();

        for (int x = 0; x <= 8; x++) {
            for (int y = 0; y <= 5; y++) {
                for (FossilType fossil : possibleFossilTypes) {
                    for (FossilMutation mutation : fossil.getPossibleMutations()) {
                        FossilShape modifiedShape = mutation.getModification().apply(fossil.getFossilShape());
                        FossilShape newPosition = modifiedShape.moveTo(x, y);

                        if (!isValidFossilPosition(newPosition, invalidPositions, foundPositions)) {
                            continue;
                        }

                        for (FossilTile position : newPosition.getTiles()) {
                            possibleClickPositions.merge(position, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        List<FossilTile> toRemove = new ArrayList<>();
        for (FossilTile key : possibleClickPositions.keySet()) {
            if (foundPositions.contains(key)) {
                toRemove.add(key);
            }
        }
        for (FossilTile key : toRemove) {
            possibleClickPositions.remove(key);
        }

        FossilTile bestPosition = null;
        int bestValue = -1;
        for (Map.Entry<FossilTile, Integer> entry : possibleClickPositions.entrySet()) {
            if (entry.getValue() > bestValue) {
                bestValue = entry.getValue();
                bestPosition = entry.getKey();
            }
        }

        return bestPosition;
    }

    private static boolean isValidFossilPosition(FossilShape fossil, Set<FossilTile> invalidPositions, Set<FossilTile> foundPositions) {
        for (FossilTile tile : fossil.getTiles()) {
            if (!isValidPosition(tile, invalidPositions)) {
                return false;
            }
        }

        for (FossilTile pos : foundPositions) {
            boolean found = false;
            for (FossilTile tile : fossil.getTiles()) {
                if (tile.equals(pos)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPosition(FossilTile fossil, Set<FossilTile> invalidPositions) {
        if (invalidPositions.contains(fossil)) return false;
        return fossil.x() >= 0 && fossil.y() >= 0 && fossil.x() < 9 && fossil.y() < 6;
    }
}
