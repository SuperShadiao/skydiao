package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.booleans.BooleanBooleanPair;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

@Mixin(Minecraft.class)
public class MixinInputSimulatorHook {

    @Shadow
    private int rightClickDelay;
    @Shadow
    public int missTime;

    @Unique
    private boolean hookedLeftClickResult;

    @Unique
    private boolean consumedHookedLeftClick;
    @Unique
    private boolean consumedHookedRightClick;
    @Unique
    private boolean shouldConsumeClicks;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;tick()V"))
    public void tickGui(Gui instance, Operation<Void> original) {
        consumedHookedLeftClick = false;
        consumedHookedRightClick = false;

        shouldConsumeClicks = ToolList.mc.player != null && !ToolList.mc.player.isUsingItem();
        original.call(instance);
        if(ToolList.mc.player != null && ToolList.mc.level != null && ToolList.mc.gameMode != null) {
            InputSimulator.updateTick(instance.screen() != null);
        }
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    public boolean shouldConsumeClicks(LocalPlayer instance, Operation<Boolean> original) {
        return !(shouldConsumeClicks = !original.call(instance));
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
    public boolean onConsumeClick(KeyMapping instance, Operation<Boolean> original) {
        if(instance == ToolList.mc.options.keyAttack) {
            if(shouldConsumeClicks) {
                // 先消费并执行模拟点击，再让原版点击走 original（避免同一 tick 里模拟点击被丢或重复）
                BooleanBooleanPair pair = InputSimulator.consumeSingleLeftClick();
                if (pair.rightBoolean()) {
                    hookedLeftClickResult = pair.leftBoolean();
                    consumedHookedLeftClick = true;
                    return true;
                }
                consumedHookedLeftClick = false;
            } else {
                InputSimulator.consumeDoNothing();
            }
        } else if(instance == ToolList.mc.options.keyUse) {
            if(shouldConsumeClicks) {
                if (InputSimulator.consumeSingleRightClick()) {
                    consumedHookedRightClick = true;
                    return true;
                } else {
                    consumedHookedRightClick = false;
                }
            } else {
                InputSimulator.consumeDoNothing();
            }
        }
        return original.call(instance);
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startAttack()Z"))
    public boolean consumeLeftClick(Minecraft instance, Operation<Boolean> original) {
        if(consumedHookedLeftClick) {
            consumedHookedLeftClick = false;
            return hookedLeftClickResult;
        }
        return original.call(instance);
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V"))
    public void consumeRightClick(Minecraft instance, Operation<Void> original) {
        if(consumedHookedRightClick) {
            consumedHookedRightClick = false;
            return;
        }
        original.call(instance);
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    public void handleKeyBinds(Minecraft instance, boolean bl, Operation<Void> original) {
        boolean click = bl || InputSimulator.leftClickFlagMinecraft;
        boolean executed = false;
        if(!bl && missTime > 1000) {
            InputSimulator.continueAttack(click);
            executed = click;
            if (executed) missTime = 0;
        }
        if(!executed) {
            original.call(instance, click);
            if(missTime == 0) {
                InputSimulator.missTime = 0;
            }
        }
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    public boolean syncRightClickDelay(KeyMapping instance, Operation<Boolean> original) {
        if(InputSimulator.rightClickDelay != 0) {
            this.rightClickDelay = InputSimulator.rightClickDelay;
        }
        if(instance == ToolList.mc.options.keyUse) {
            return original.call(instance) || (InputSimulator.isMouseRightHolding && !InputSimulator.isInventoryOpen());
        }
        return original.call(instance);
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    public void startUseItem(CallbackInfo ci) {
        InputSimulator.rightClickDelay = this.rightClickDelay;
    }

    @WrapOperation(method = "pauseGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;stopDestroyBlock()V"))
    public void cancelIfSimulatorRunning(MultiPlayerGameMode instance, Operation<Void> original) {
        if(!InputSimulator.leftClickFlagMinecraft) original.call(instance);
    }

}
