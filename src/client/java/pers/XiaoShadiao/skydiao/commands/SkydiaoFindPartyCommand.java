package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.irc.XSDIRCConfig;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatClientManager;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatPacket;
import pers.XiaoShadiao.skydiao.screen.partyfinder.PartyListScreen;
import pers.XiaoShadiao.skydiao.screen.partyfinder.SetupPartyScreen;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderManager;

import java.util.List;

public class SkydiaoFindPartyCommand extends BaseRootRunnableCommand {

    @Override
    public String getCommandName() {
        return "skydiaofindparty";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgConstantInstance("setup").executes(context -> {
                    mc.schedule(() -> mc.gui.setScreen(new SetupPartyScreen()));
                    return 0;
                }),
                getArgConstantInstance("find").executes(this::executeCommand),
                getArgConstantInstance("broadcast").executes(context -> {
                    if (!PartyFinderManager.isPublic) {
                        context.getSource().sendError(Component.literal("§a[小沙雕] §c队伍当前未公开, 不能进行广播, 请使用§6/skydiaofp§c设置并公开队伍"));
                    } else if(PartyFinderManager.selfPartyData.isPartyFull()) {
                        context.getSource().sendError(Component.literal("§a[小沙雕] §c队伍已满, 不能进行广播"));
                    } else {
                        ChatPacket packet = new ChatPacket();
                        packet.message = PartyFinderManager.selfPartyData.toJson().toString();
                        packet.packetType = "find_party_broadcast";
                        packet.initSender();
                        ChatClientManager.trySendOrWarning(packet, XSDIRCConfig.INSTANCE);
                    }
                    return 0;
                }),
                getArgConstantInstance("join").then(getArgInstance("playerName", StringArgumentType.string()).executes(context -> {
                    String playerName = context.getArgument("playerName", String.class);
                    PartyFinderManager.joinPlayerNameParty(playerName);
                    return 0;
                }))
        );
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        mc.schedule(() -> mc.gui.setScreen(new PartyListScreen()));
        return 0;
    }

}
