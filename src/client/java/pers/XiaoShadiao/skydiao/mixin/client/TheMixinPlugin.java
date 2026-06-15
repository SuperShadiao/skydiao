package pers.XiaoShadiao.skydiao.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class TheMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
//        return switch (mixinClassName) {
//            case "pers.XiaoShadiao.skydiao.mixin.client.MixinFogRendererRemoveBlindness_1_21_10"
//                    -> getVersion().equals("1.21.10");
//            case "pers.XiaoShadiao.skydiao.mixin.client.MixinFogRendererRemoveBlindness_1_21_11"
//                    -> getVersion().equals("1.21.11");
//
//            default -> true;
//        };
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    public static String getVersion() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();
    }

}
