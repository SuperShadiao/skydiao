package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

@Mixin(FogRenderer.class)
public class MixinFogRendererRemoveBlindness {

    @Mutable
    @Final
    @Shadow
    private static List<FogEnvironment> FOG_ENVIRONMENTS;
    @Unique
    private static final FogEnvironment noLava = new FogEnvironment() {
        @Override
        public void setupFog(FogData fogData, Camera camera, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
            fogData.environmentalStart = f * 0.05F;
            fogData.environmentalEnd = Math.min(f, 192.0F) * 0.5F;
            fogData.skyEnd = fogData.environmentalEnd;
            fogData.cloudEnd = fogData.environmentalEnd;
        }

        @Override
        public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
            return fogType == FogType.LAVA;
        }
    };
    @Unique
    private static final List<FogEnvironment>
            original = List.of(
            new LavaFogEnvironment(),
            new PowderedSnowFogEnvironment(),
            new BlindnessFogEnvironment(),
            new DarknessFogEnvironment(),
            new WaterFogEnvironment(),
            new AtmosphericFogEnvironment()
    ),
            modified = List.of(
                    noLava,// new LavaFogEnvironment(),
                    new PowderedSnowFogEnvironment(),
                    // new BlindnessFogEnvironment(),
                    // new DarknessFogEnvironment(),
                    new WaterFogEnvironment(),
                    new AtmosphericFogEnvironment()
            );

    @Inject(method = "setupFog", at = @At("HEAD"), require = 0)
    public void setupFog(Camera camera, int i, DeltaTracker deltaTracker, float f, ClientLevel clientLevel, CallbackInfoReturnable<Vector4f> cir) {
        FOG_ENVIRONMENTS = new ArrayList<>(ConfigManager.noblind.getValue() ? modified : original);
    }

}
