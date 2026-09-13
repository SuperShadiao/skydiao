package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class F7ArrowAlignListener extends AbstractListener implements IDungeonListener {

    private static final BlockPos puzzleCorn1 = new BlockPos(-2, 124, 79);
    private static final BlockPos puzzleCorn2 = new BlockPos(-2, 120, 75);
    private static final BlockPos puzzleWallCorn1 = new BlockPos(-3, 125, 74);
    private static final BlockPos puzzleWallCorn2 = new BlockPos(-3, 119, 80);
    private static final BoundingBox puzzleArea = BoundingBox.fromCorners(puzzleCorn1, puzzleCorn2);
    private static final BoundingBox puzzleWallArea = BoundingBox.fromCorners(puzzleWallCorn1, puzzleWallCorn2);

    private int solveAfterTick = 0;

    private final Object2IntMap<ItemFrame> remainClicks = new Object2IntLinkedOpenHashMap<>();

    @Override
    public String getListenerName() {
        return "F7ArrowAlignListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((_, _) -> solveAfterTick = 10);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::onCollectSubmit);
        CustomFabricEvents.ON_INGAME_CLICK.register(this::onIngameClick);
    }

    private boolean onIngameClick(CustomFabricEvents.ClickType clickType) {
        if (mc.player == null || mc.level == null || !ConfigManager.f7ArrowAlignSolver.getValue()) return false;
        if(clickType == CustomFabricEvents.ClickType.RIGHT) {
            if (mc.hitResult instanceof EntityHitResult hitResult && hitResult.getEntity() instanceof ItemFrame itemFrame) {
                if (remainClicks.containsKey(itemFrame)) {
                    int remainClick = remainClicks.getInt(itemFrame);
                    boolean sneak = mc.player.hasPose(Pose.CROUCHING);
                    if(remainClick == 0 && !sneak && ConfigManager.f7ArrowAlignSolverBlockWrongClicks.getValue()) {
                        return true;
                    }
                    remainClicks.put(itemFrame, (remainClick - 1 + 8) % 8);
                    solveAfterTick = 40;
                }
            }
        }
        return false;
    }

    private void onCollectSubmit(LevelRenderContext context) {
        for (Object2IntMap.Entry<ItemFrame> remainClickEntry : remainClicks.object2IntEntrySet()) {
            if(remainClickEntry.getIntValue() != 0) RenderUtils.renderNameTag(context, remainClickEntry.getKey().position(), Component.literal("§c" + remainClickEntry.getIntValue()));
        }
    }

    private void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !ConfigManager.f7ArrowAlignSolver.getValue()) return;
        if (solveAfterTick > 0) {
            solveAfterTick--;
            if(solveAfterTick == 0) {
                solvePuzzle();
            }
        }
        if (solveAfterTick <= 0) {
            solveAfterTick = 40;
        }
    }

    private void solvePuzzle() {
        if (mc.player == null || mc.level == null) return;
        List<ItemFrame> itemFrames = mc.level.getEntitiesOfClass(ItemFrame.class, AABB.of(puzzleArea));
        Map<BlockPos, ItemFrame> bpToFrame = new HashMap<>();
        Object2IntMap<ItemFrame> targetState = new Object2IntLinkedOpenHashMap<>();
        List<ItemFrame> startNode = new ArrayList<>();// itemFrames.stream().filter(frame -> frame.getItem().getItem() == Items.LIME_WOOL).toList();
        List<ItemFrame> endNode = new ArrayList<>();// itemFrames.stream().filter(frame -> frame.getItem().getItem() == Items.RED_WOOL).toList();
        for (ItemFrame itemFrame : itemFrames) {
            if(itemFrame.getItem().getItem() == Items.LIME_WOOL) {
                startNode.add(itemFrame);
            } else if(itemFrame.getItem().getItem() == Items.RED_WOOL) {
                endNode.add(itemFrame);
            } else if(itemFrame.getItem().getItem() != Items.ARROW) {
                continue;
            }
            bpToFrame.put(itemFrame.blockPosition(), itemFrame);
        }

        // =====================================

        for (ItemFrame startFrame : startNode) {
            ArrowNode startArrowNode = new ArrowNode();
            startArrowNode.itemFrame = startFrame;
            List<PuzzleResult> puzzleResults = tryExploreNode(bpToFrame, targetState, startNode, endNode, startArrowNode);

            if(!(puzzleResults.isEmpty())) {
                for (PuzzleResult puzzleResult : puzzleResults) {
                    ArrowNode currentArrowNode = puzzleResult.startArrowNode;
                    while (true) {
                        ArrowNode nextArrowNode = currentArrowNode.next;
                        if (nextArrowNode == null) break;
                        if (!startNode.contains(currentArrowNode.itemFrame) && !endNode.contains(currentArrowNode.itemFrame)) {
                            BlockPos offset = nextArrowNode.itemFrame.blockPosition().subtract(currentArrowNode.itemFrame.blockPosition());
                            if (offset.getY() > 0) targetState.put(currentArrowNode.itemFrame, 7);
                            else if (offset.getY() < 0) targetState.put(currentArrowNode.itemFrame, 3);
                            else if (offset.getZ() < 0) targetState.put(currentArrowNode.itemFrame, 1);
                            else if (offset.getZ() > 0) targetState.put(currentArrowNode.itemFrame, 5);
                        }
                        currentArrowNode = nextArrowNode;
                    }
                }
            }
        }
        remainClicks.clear();
        targetState.forEach((itemFrame, targetState2) -> remainClicks.put(itemFrame, (targetState2 + 8 - itemFrame.getRotation()) % 8));
    }

    private static final BlockPos[] offsets = {
        new BlockPos(0, 0, 1),
        new BlockPos(0, 0, -1),
        new BlockPos(0, 1, 0),
        new BlockPos(0, -1, 0),
    };

    private record PuzzleResult(ArrowNode startArrowNode, ArrowNode endArrowNode) { }

    private List<PuzzleResult> tryExploreNode(Map<BlockPos, ItemFrame> bpToFrame, Object2IntMap<ItemFrame> targetState, List<ItemFrame> startNode, List<ItemFrame> endNode, ArrowNode arrowNode) {
        List<PuzzleResult> puzzleResults = new ArrayList<>();
        BlockPos pos = arrowNode.itemFrame.blockPosition();
        out:for (BlockPos offset : offsets) {
            BlockPos offsetPos = pos.offset(offset);
            ItemFrame newItemFrame = bpToFrame.get(offsetPos);
            if(newItemFrame == null) continue;
            arrowNode = arrowNode.clone();

            ArrowNode newArrowNode = new ArrowNode();
            newArrowNode.prev = arrowNode;
            arrowNode.next = newArrowNode;
            newArrowNode.itemFrame = newItemFrame;
            ArrowNode endArrowNode = exploreNode(bpToFrame, startNode, endNode, newArrowNode);
            if(endArrowNode != null) {
                puzzleResults.add(new PuzzleResult(arrowNode, endArrowNode));
            }
        }

        return puzzleResults;
    }

    private ArrowNode exploreNode(Map<BlockPos, ItemFrame> bpToFrame, List<ItemFrame> startNode, List<ItemFrame> endNode, ArrowNode arrowNode) {
        BlockPos pos = arrowNode.itemFrame.blockPosition();
        out:for (BlockPos offset : offsets) {
            BlockPos offsetPos = pos.offset(offset);
            // if(arrowNode.prev != null && arrowNode.prev.frame.blockPosition().equals(offsetPos)) continue;
            ArrowNode currentPrev = arrowNode;
            while(true) {
                ArrowNode nextPrev = currentPrev.prev;
                if(nextPrev == null) break;
                if(nextPrev.itemFrame.blockPosition().equals(offsetPos)) continue out;
                currentPrev = nextPrev;
            }
            ItemFrame newItemFrame = bpToFrame.get(offsetPos);
            if(startNode.contains(newItemFrame)) continue;
            if(newItemFrame != null) {
                ArrowNode newArrowNode = new ArrowNode();
                newArrowNode.prev = arrowNode;
                arrowNode.next = newArrowNode;
                newArrowNode.itemFrame = newItemFrame;
                if(endNode.contains(newItemFrame)) {
                    return newArrowNode;
                } else {
                    ArrowNode newNextArrowNode = exploreNode(bpToFrame, startNode, endNode, newArrowNode);
                    if(newNextArrowNode != null) return newNextArrowNode;
                }
            }
        }
        return null;
    }

    public static final class ArrowNode implements Cloneable {
        public ArrowNode prev;
        public ArrowNode next;
        public ItemFrame itemFrame;

        public ArrowNode clone() {
            try {
                return (ArrowNode) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

    @Override
    public int getFloor() {
        return 7;
    }

}
