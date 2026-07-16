package pers.XiaoShadiao.skydiao.server.utils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class ToolList {

    private static final ToolList INSTANCE = new ToolList();

    private ToolList() {

    }

    public static ToolList getInstance() {
        return INSTANCE;
    }

    public boolean isXiaoShadiaoPlayer(Entity entity) {
        return entity instanceof Player player && player.getName().getString().equals("5i_XiaoShadiao");
    }

}
