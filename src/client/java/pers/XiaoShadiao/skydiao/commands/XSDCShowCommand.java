package pers.XiaoShadiao.skydiao.commands;

import com.google.gson.JsonElement;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;
import java.util.function.Supplier;

public class XSDCShowCommand extends BaseRootRunnableCommand {

    @Override
    public String getCommandName() {
        return "xsdcshow";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgConstantInstance("hand").executes((c) -> showItemBySlot(SlotShow.HANDITEM)),
                getArgConstantInstance("helmet").executes((c) -> showItemBySlot(SlotShow.HELMET)),
                getArgConstantInstance("chestplate").executes((c) -> showItemBySlot(SlotShow.CHESTPLATE)),
                getArgConstantInstance("legging").executes((c) -> showItemBySlot(SlotShow.LEGGING)),
                getArgConstantInstance("boots").executes((c) -> showItemBySlot(SlotShow.BOOTS))
        );
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        return showItemBySlot(SlotShow.HANDITEM);
    }

    public enum SlotShow {
        HELMET(() -> ToolList.mc.player.getItemBySlot(EquipmentSlot.HEAD)),
        CHESTPLATE(() -> ToolList.mc.player.getItemBySlot(EquipmentSlot.CHEST)),
        LEGGING(() -> ToolList.mc.player.getItemBySlot(EquipmentSlot.LEGS)),
        BOOTS(() -> ToolList.mc.player.getItemBySlot(EquipmentSlot.FEET)),
        HANDITEM(() -> ToolList.mc.player.getItemBySlot(EquipmentSlot.MAINHAND)),
        ;

        public final Supplier<ItemStack> getter;

        SlotShow(Supplier<ItemStack> o) {
            getter = o;
        }

        public ItemStack get() {
            return getter.get();
        }
    }

    private int showItemBySlot(SlotShow slot) {
        if(slot == null) slot = SlotShow.HANDITEM;
        showItem(slot.get());
        return 0;
    }

    private void showItem(ItemStack itemStack) {
        if(!itemStack.isEmpty() && mc.level != null) {
            JsonElement jsonElement = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess()), itemStack).getOrThrow();
            ChatPacket packet = new ChatPacket();
            packet.packetType = "showitem";
            packet.message = jsonElement.toString();
            packet.initSender();
            ChatClientManager.trySendOrWarning(packet);
        }
    }

}
