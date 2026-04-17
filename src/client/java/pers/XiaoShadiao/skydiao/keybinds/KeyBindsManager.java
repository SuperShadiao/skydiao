package pers.XiaoShadiao.skydiao.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import pers.XiaoShadiao.skydiao.utils.Register;

public class KeyBindsManager {

    public static final KeyMapping.Category skydiaoCategory = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skydiao", "skydiao"));

    public static final KeyMapping leftAutoClickerSwap = new KeyMapping("key.category.skydiao.leftautoclicker", InputConstants.Type.KEYSYM, -1, skydiaoCategory);
    public static final KeyMapping rightAutoClickerSwap = new KeyMapping("key.category.skydiao.rightautoclicker", InputConstants.Type.KEYSYM, -1, skydiaoCategory);

    public static void registerKeyBinds() {
        Register.execRegister(KeyBindsManager.class, KeyMapping.class, KeyBindingHelper::registerKeyBinding);
    }

}
