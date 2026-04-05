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
    public static final BooleanConfigOption dungeonRenderDangerousEnemy = new BooleanConfigOption("skyblockdungeonenemydisplay", false);
    public static final BooleanConfigOption skyblockSafeIsland = new BooleanConfigOption("skyblocksafeisland", true);
    public static final BooleanConfigOption dungeonRenderTraps = new BooleanConfigOption("skyblockdungeontraprender", false);
    public static final BooleanConfigOption autoEnchantTableGame = new BooleanConfigOption("sbautoplayenchant", false);
    public static final BooleanConfigOption autoHarp = new BooleanConfigOption("skyblockautoplayharp", false);

    public static final BooleanConfigOption bossbar = new BooleanConfigOption("bossbar", false);
    public static final BooleanConfigOption bossbarShowHealth = new BooleanConfigOption("bossbarshowhealth", false);
    public static final BooleanConfigOption bossbarAddTargetEntity = new BooleanConfigOption("bossbaraddtargetentity", false);
    public static final IntConfigOption bossbarDisplayLimit = new IntConfigOption("bossbardisplaylimit", 3);
    public static final BooleanConfigOption noblind = new BooleanConfigOption("removeblindnessrender", false);
    public static final BooleanConfigOption nosuffoverlay = new BooleanConfigOption("nosuffoverlay", false);
    public static final BooleanConfigOption inventoryFilter = new BooleanConfigOption("skyblockinventoryitemfilter", false);
    public static final BooleanConfigOption dyingtip = new BooleanConfigOption("dyingtip", false);
    public static final StringConfigOption inventoryFilterRegex = new StringConfigOption("inventoryfilterregex", "");
    public static final StringConfigOption inventoryFilterRegexList = new StringConfigOption("inventoryfilterregexlist", "[]");
    public static final BooleanConfigOption autoFish = new BooleanConfigOption("autofish", false).flagAsMacroFeature();
    public static final BooleanConfigOption autoFishAutoJump = new BooleanConfigOption("autofishautojump", true).flagAsMacroFeature();
    public static final BooleanConfigOption autoFishAutoMove = new BooleanConfigOption("autofishautomove", false).flagAsMacroFeature();
    public static final BooleanConfigOption autoFishAutoRotation = new BooleanConfigOption("autofishautorotation", true).flagAsMacroFeature();
    public static final BooleanConfigOption pfAllowBreak = new BooleanConfigOption("pathfinderallowbreak", false);
    public static final BooleanConfigOption pfAllowPlace = new BooleanConfigOption("pathfinderallowplace", false);
    public static final BooleanConfigOption pfStopWhenTP = new BooleanConfigOption("pathfinderstopwhentp", false);
    public static final IntConfigOption pfTimeout = new IntConfigOption("pathfindertimeout", 60000);
    public static final BooleanConfigOption pathfinderallowbreakwhengetslowmining = new BooleanConfigOption("pathfinderallowbreakwhengetslowmining", false);
    public static final BooleanConfigOption pfXRay = new BooleanConfigOption("pathfinderxray", false);
    public static final BooleanConfigOption autoDojo = new BooleanConfigOption("autodojo", false);
    public static final IntConfigOption autoDojoControlPredictDist = new IntConfigOption("autodojocontrolpredictdist", 8);
    public static final BooleanConfigOption necronLadderNotification = new BooleanConfigOption("necronladdernotification", true);
    public static final BooleanConfigOption dungeonf7msgbot = new BooleanConfigOption("dungeonf7msgbot", true).addDependFeature(bossbar);
    public static final StringConfigOption dungeonf7msgbotsimonsaysstart = new StringConfigOption("dungeonf7msgbotsimonsaysstart", "Simon Says开始咯!");
    public static final StringConfigOption dungeonf7msgbotsimonsays = new StringConfigOption("dungeonf7msgbotsimonsays", "Simon Says已完成 [p]...");
    public static final StringConfigOption dungeonf7msgbotcoretunnel = new StringConfigOption("dungeonf7msgbotcoretunnel", "已进入Goldor核心隧道!");

    public static final BooleanConfigOption chatbutton = new BooleanConfigOption("chatbutton", false).setRequiredMod(new ConfigOption.ModDepends("chatpatches", "alpha.8", "https://modrinth.com/mod/chatpatches"));

    public static final List<Map.Entry<String, List<ConfigOption<?, ?>>>> categories = List.of(
            Map.entry("basic", List.of(language)),
            Map.entry("工具类", List.of(inventoryFilter, chatbutton)),
            Map.entry("寻路系统", List.of(pfAllowBreak, pfAllowPlace, pfStopWhenTP, pfTimeout, pathfinderallowbreakwhengetslowmining, pfXRay)),
            Map.entry("自动类", List.of(autoEnchantTableGame, autoHarp, autoFish, autoFishAutoJump, autoFishAutoMove, autoFishAutoRotation, autoDojo, autoDojoControlPredictDist)),
            Map.entry("mining", List.of(mineshaftSharing, skyblockSafeIsland)),
            Map.entry("dungeon", List.of(dungeonRenderDangerousEnemy, dungeonRenderTraps, necronLadderNotification, dungeonf7msgbot, dungeonf7msgbotsimonsaysstart, dungeonf7msgbotsimonsays, dungeonf7msgbotcoretunnel)),
            Map.entry("界面类", List.of(bossbar, bossbarShowHealth, bossbarAddTargetEntity, bossbarDisplayLimit, dyingtip, noblind, nosuffoverlay))
    );


    /* ==================init================== */

    public static final List<ConfigOption<?, ?>> optionList = Arrays.stream(ConfigManager.class.getDeclaredFields()).filter(field -> ConfigOption.class.isAssignableFrom(field.getType())).map(field -> {
        try {
            field.setAccessible(true);
            return Objects.requireNonNull((ConfigOption<?, ?>) field.get(null), field.getName());
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException("Error getting config option", e);
        }
    }).collect(Collectors.toList());

    public static void saveConfig() {
        JsonObject jo = new JsonObject();
        for (ConfigOption<?, ?> option : optionList) {
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
            e.printStackTrace();
        }
    }

    static {
        readConfig();
    }

    private static void readConfig() {
        try {
            JsonObject jo = JsonParser.parseString(FileUtils.readFileToString(configFile, StandardCharsets.UTF_8)).getAsJsonObject();
            for (ConfigOption<?, ?> option : optionList) {
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
