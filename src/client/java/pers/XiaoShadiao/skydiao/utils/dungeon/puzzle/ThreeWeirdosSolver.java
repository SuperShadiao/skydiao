package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Three Weirdos 谜题：三个 NPC 各说一句话，其中一句能确定奖励在哪个箱子，箱子用绿色方块标出来。
 *
 * <p>搬运自 Skyblocker 的 {@code ThreeWeirdos}。NPC 站在相对坐标 (13,69,24)、(15,69,25)、
 * (17,69,24)，对应的箱子在各自 +1 x 的位置。说话人通过 NPC 名字和盔甲架实体对上。
 */
public final class ThreeWeirdosSolver extends DungeonPuzzleSolver {

    /** 和 Skyblocker 完全一致的 NPC 台词匹配，组 1 是说话人名字。 */
    private static final Pattern PATTERN = Pattern.compile(
            "^\\[NPC] ([A-Z][a-z]+): (?:The reward is(?: not in my chest!|n't in any of our chests\\.)"
                    + "|My chest (?:doesn't have the reward\\. We are all telling the truth\\.|has the reward and I'm telling the truth!)"
                    + "|At least one of them is lying, and the reward is not in [A-Z][a-z]+'s chest!"
                    + "|Both of them are telling the truth\\. Also, [A-Z][a-z]+ has the reward in their chest!)$");

    private static final BlockPos[] NPC_POSITIONS = {
            new BlockPos(13, 69, 24),
            new BlockPos(15, 69, 25),
            new BlockPos(17, 69, 24)
    };

    private BlockPos chest;

    public ThreeWeirdosSolver() {
        super("three-chests", "three-weirdos");
    }

    @Override
    public void tick(Minecraft mc, RoomMatcher.Match room) {
        // 箱子已经被打开说明谜题做完了，不用再标
        if (chest == null || mc.level == null) return;
        BlockEntity blockEntity = mc.level.getBlockEntity(chest);
        if (blockEntity instanceof ChestBlockEntity opened && opened.getOpenNess(0f) > 0.01f) {
            chest = null;
        }
    }

    @Override
    public boolean onGameMessage(Component message, boolean overlay, RoomMatcher.Match room) {
        if (overlay || room == null) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return true;

        Matcher matcher = PATTERN.matcher(ChatFormatting.stripFormatting(message.getString()));
        if (!matcher.matches()) return true;
        String name = matcher.group(1);

        for (BlockPos relative : NPC_POSITIONS) {
            BlockPos npcPos = room.relativeToActual(relative);
            List<ArmorStand> npcs = mc.level.getEntitiesOfClass(ArmorStand.class, new AABB(npcPos),
                    entity -> entity.getName().getString().equals(name));
            if (!npcs.isEmpty()) {
                chest = room.relativeToActual(relative.offset(1, 0, 0));
                break;
            }
        }
        return true;
    }

    @Override
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
        if (chest == null) return;
        RenderUtils.WorldRender fill = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        RenderUtils.renderESP(fill, chest, 0f, 1f, 0f, 0.5f, true, true);
        fill.finishDraw();
    }

    @Override
    public void reset() {
        chest = null;
    }
}
