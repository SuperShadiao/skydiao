package pers.XiaoShadiao.skydiao.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.config.option.*;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ConfigManager {
    public static final File config_folder = Paths.get(ToolList.mc.gameDirectory.getPath(), "config", "小沙雕_config", "skydiao").toFile();
    public static final File configFile = new File(config_folder, "config.json");

    public static final BooleanConfigOption mineshaftSharing = new BooleanConfigOption("skyblockmineshaftsharing", true);
    public static final SelectConfigOption language = new SelectConfigOption("language", CrowdinI18nManager.fromSystemLanguage().ordinal(), Arrays.stream(CrowdinI18nManager.LangCode.values()).map(v -> v.displayName).toList());

    public static final List<ConfigOption<?>> optionList = Arrays.stream(ConfigManager.class.getDeclaredFields()).filter(field -> ConfigOption.class.isAssignableFrom(field.getType())).map(field -> {
        try {
            field.setAccessible(true);
            return Objects.requireNonNull((ConfigOption<?>) field.get(null), field.getName());
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException("Error getting config option", e);
        }
    }).collect(Collectors.toList());

    public static final List<Map.Entry<String, List<ConfigOption<?>>>> categories = List.of(
            Map.entry("basic", List.of(language)),
            Map.entry("mining", List.of(mineshaftSharing))
    );

    public static void saveConfig() {
        JsonObject jo = new JsonObject();
        for (ConfigOption<?> option : optionList) {
            Object value = option.getValue();
            if (value instanceof Boolean) {
                jo.addProperty(option.getName(), (Boolean) value);
            } else if (value instanceof Integer) {
                jo.addProperty(option.getName(), (Integer) value);
            } else if (value instanceof String) {
                jo.addProperty(option.getName(), (String) value);
            }
        }
        try {
            FileUtils.writeStringToFile(configFile, jo.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        readConfig();
    }

    private static void readConfig() {
        try {
            JsonObject jo = JsonParser.parseString(FileUtils.readFileToString(configFile, StandardCharsets.UTF_8)).getAsJsonObject();
            for (ConfigOption<?> option : optionList) {
                if (jo.has(option.getName())) {
                    try {
                        if(option instanceof BooleanConfigOption booleanOption) {
                            booleanOption.setValue(jo.get(option.getName()).getAsBoolean());
                        } else if(option instanceof SelectConfigOption selectOption) {
                            selectOption.setValue(jo.get(option.getName()).getAsInt());
                        } else if(option instanceof StringConfigOption stringOption) {
                            stringOption.setValue(jo.get(option.getName()).getAsString());
                        } else if(option instanceof IntConfigOption intgOption) {
                            intgOption.setValue(jo.get(option.getName()).getAsInt());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
