package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MixinMinecraftSessionAccessor {

    @Invoker("setUser")
    public void setUser(User user);

    @Accessor("profileKeyPairManager")
    public ProfileKeyPairManager getProfileKeyPairManager();

}
