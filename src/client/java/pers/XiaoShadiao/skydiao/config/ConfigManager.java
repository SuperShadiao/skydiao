package pers.XiaoShadiao.skydiao.config;

import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.File;
import java.nio.file.Paths;

public class ConfigManager {
    public static final File config_folder = Paths.get(ToolList.mc.gameDirectory.getPath(), "config", "小沙雕_config", "skydiao").toFile();

}
