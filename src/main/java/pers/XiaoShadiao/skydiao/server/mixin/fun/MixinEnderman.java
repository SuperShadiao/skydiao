package pers.XiaoShadiao.skydiao.server.mixin.fun;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderMan.class)
public abstract class MixinEnderman extends Monster implements NeutralMob {

    protected MixinEnderman(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Inject(method = "registerGoals", at = @At("RETURN"))
    protected void registerGoals(CallbackInfo ci) {
        this.targetSelector.addGoal(10, new NearestAttackableTargetGoal<>(this, EnderMan.class, true, true));
    }

}
