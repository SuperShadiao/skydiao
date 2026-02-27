package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.utils.HypixelRewardClaimer;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public class HHSCCommand extends OpenConfigMenuCommand {

    @Override
    public String getCommandName() {
        return "hhsc";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgConstantInstance("listmobinfo").executes(this::executePrintMobInfo),
                getArgConstantInstance("claimreward").then(getArgInstance("index", IntegerArgumentType.integer(0, 2)).executes(this::executeClaimReward))
        );
    }

    private int executeClaimReward(CommandContext<FabricClientCommandSource> context) {
        HypixelRewardClaimer hrc = HypixelRewardClaimer.getCurrent();
        if(hrc != null && hrc.hasData && !hrc.claimed) {
            hrc.setTargetReward(IntegerArgumentType.getInteger(context, "index"));
            hrc.doClaim();
        }
        return 0;
    }

    @Override
    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        super.lastCallRootCmdNode(rootCmdNode);
        // rootCmdNode.redirect(OPEN_CONFIG_MENU_COMMAND.getCommandNode());
    }

    private int executePrintMobInfo(CommandContext<FabricClientCommandSource> context) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            E2AMappingListener.MobInfo mobInfo = AbstractListener.e2AMappingListener.getMobInfo(entity.asLivingEntity());
            if(mobInfo != null) {
                ToolList.printChatMessage(Component.literal(entity.toString()));
                ToolList.printChatMessage(Component.literal(mobInfo.armorStand.toString()));
                ToolList.printChatMessage(Component.empty());
            }
        }
        return 1;
    }

}
