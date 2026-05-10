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

    public static final BooleanConfigOption bossbar = new BooleanConfigOption("bossbar", true);
    public static final BooleanConfigOption bossbarShowHealth = new BooleanConfigOption("bossbarshowhealth", true);
    public static final BooleanConfigOption bossbarAddTargetEntity = new BooleanConfigOption("bossbaraddtargetentity", true);
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
    public static final BooleanConfigOption dungeonf7autoterm = new BooleanConfigOption("dungeonf7autoterm", false);
    public static final BooleanConfigOption enablexsdccommandtip = new BooleanConfigOption("enablexsdccommandtip", true);
    public static final BooleanConfigOption enableircjointip = new BooleanConfigOption("enableircjointip", true);
    public static final SelectConfigOption fireOverlay = new SelectConfigOption("fireoverlay", 1, List.of("config.fireoverlay.options.normal", "config.fireoverlay.options.lower", "config.fireoverlay.options.remove"));
    public static final BooleanConfigOption skydiaocustomcape = new BooleanConfigOption("skydiaocustomcape", false);
    public static final BooleanConfigOption skyblockriftautodanceroom = new BooleanConfigOption("skyblockriftautodanceroom", false);
    public static final BooleanConfigOption mineshaftHelper = new BooleanConfigOption("mineshafthelper", true);
    public static final BooleanConfigOption galateashulker = new BooleanConfigOption("galateashulker", true);
    public static final IntConfigOption dungeonf7autotermclickdelay = new IntConfigOption("dungeonf7autotermclickdelay", 270);
    public static final BooleanConfigOption rifttimegunhelper = new BooleanConfigOption("rifttimegunhelper", false);
    public static final BooleanConfigOption slayerTogether = new BooleanConfigOption("slayertogether", true);
    public static final BooleanConfigOption resurrectionItemTriggeredTitle = new BooleanConfigOption("resurrectionitemtriggeredtitle", true);
    public static final BooleanConfigOption hubratesp = new BooleanConfigOption("hubratesp", true);
    public static final BooleanConfigOption hotspotrender = new BooleanConfigOption("hotspotrender", true);

    public static final BooleanConfigOption dungeonf7msgbot = new BooleanConfigOption("dungeonf7msgbot", true);
    public static final StringConfigOption dungeonf7msgbotsimonsaysstart = new StringConfigOption("dungeonf7msgbotsimonsaysstart", "Simon Says开始咯!");
    public static final StringConfigOption dungeonf7msgbotsimonsays1 = new StringConfigOption("dungeonf7msgbotsimonsays1", "Simon Says已完成 1/5...");
    public static final StringConfigOption dungeonf7msgbotsimonsays2 = new StringConfigOption("dungeonf7msgbotsimonsays2", "Simon Says已完成 2/5...");
    public static final StringConfigOption dungeonf7msgbotsimonsays3 = new StringConfigOption("dungeonf7msgbotsimonsays3", "Simon Says已完成 3/5...");
    public static final StringConfigOption dungeonf7msgbotsimonsays4 = new StringConfigOption("dungeonf7msgbotsimonsays4", "Simon Says已完成 4/5...");
    public static final StringConfigOption dungeonf7msgbotsimonsays5 = new StringConfigOption("dungeonf7msgbotsimonsays5", "Simon Says已完成 5/5...");
    public static final StringConfigOption dungeonf7msgbotmelodystart = new StringConfigOption("dungeonf7msgbotmelodystart", "Melody终端开始咯!");
    public static final StringConfigOption dungeonf7msgbotmelody1 = new StringConfigOption("dungeonf7msgbotmelody1", "Melody终端已完成 1/4...");
    public static final StringConfigOption dungeonf7msgbotmelody2 = new StringConfigOption("dungeonf7msgbotmelody2", "Melody终端已完成 2/4...");
    public static final StringConfigOption dungeonf7msgbotmelody3 = new StringConfigOption("dungeonf7msgbotmelody3", "Melody终端已完成 3/4...");
    public static final StringConfigOption dungeonf7msgbotmelody4 = new StringConfigOption("dungeonf7msgbotmelody4", "Melody终端已完成 4/4...");
    public static final StringConfigOption dungeonf7msgbotcoretunnel = new StringConfigOption("dungeonf7msgbotcoretunnel", "已进入Goldor核心隧道!");
    public static final StringConfigOption dungeonBonzoTriggered = new StringConfigOption("dungeonbonzotriggered", "复活甲爆炸了。(Bonzo)");
    public static final StringConfigOption dungeonSpiritMaskTriggered = new StringConfigOption("dungeonspiritmasktriggered", "复活甲爆炸了。(Spirit)");
    public static final StringConfigOption dungeonPhoenixTriggered = new StringConfigOption("dungeonphoenixtriggered", "复活甲爆炸了。(Phoenix)");
    public static final BooleanConfigOption dungeonAutoCloseChest = new BooleanConfigOption("dungeonautoclosechest", false);
    public static final BooleanConfigOption dungeonPuzzleHelper = new BooleanConfigOption("dungeonpuzzlehelper", true);

    public static final StringConfigOption[] dungeonf7msgbotsimonsays = {
            dungeonf7msgbotsimonsays1,
            dungeonf7msgbotsimonsays2,
            dungeonf7msgbotsimonsays3,
            dungeonf7msgbotsimonsays4,
            dungeonf7msgbotsimonsays5
    };
    public static final StringConfigOption[] dungeonf7msgbotmelody = {
            dungeonf7msgbotmelody1,
            dungeonf7msgbotmelody2,
            dungeonf7msgbotmelody3,
            dungeonf7msgbotmelody4
    };

    public static final BooleanConfigOption chatbutton = new BooleanConfigOption("chatbutton", false).setRequiredMod(new ConfigOption.ModDepends("chatpatches", "alpha.8", "https://modrinth.com/mod/chatpatches"));

    public static final StringConfigOption hypixelhelpermusicfolder = new StringConfigOption("hypixelhelpermusicfolder", "");
    public static final BooleanConfigOption musicplayer = new BooleanConfigOption("musicplayer", true);
    public static final SelectConfigOption musicplayermode = new SelectConfigOption("musicplayermode", 0, List.of("顺序播放", "有序随机", "无序随机", "while(true)"));
    public static final IntConfigOption musiclastmusic = new IntConfigOption("musiclastmusic", 0);
    public static final IntConfigOption xsdmusicvolume = new IntConfigOption("xsdmusicvolume", 100);

    public static final List<Map.Entry<String, List<ConfigOption<?, ?>>>> categories = List.of(
            Map.entry("basic", List.of(language, enablexsdccommandtip, enableircjointip)),
            Map.entry("工具类", List.of(inventoryFilter, chatbutton, skydiaocustomcape)),
            Map.entry("寻路系统", List.of(pfAllowBreak, pfAllowPlace, pfStopWhenTP, pfTimeout, pathfinderallowbreakwhengetslowmining, pfXRay)),
            Map.entry("自动类", List.of(autoEnchantTableGame, autoHarp, autoFish, autoFishAutoJump, autoFishAutoMove, autoFishAutoRotation, autoDojo, autoDojoControlPredictDist, skyblockriftautodanceroom)),
            Map.entry("mining", List.of(mineshaftHelper, mineshaftSharing, skyblockSafeIsland)),
            Map.entry("combat", List.of(slayerTogether)),
            Map.entry("foraging", List.of(galateashulker)),
            Map.entry("farming", List.of(hubratesp)),
            Map.entry("fishing", List.of(hotspotrender)),
            Map.entry("dungeon", List.of(dungeonRenderDangerousEnemy, dungeonRenderTraps, necronLadderNotification, dungeonf7autoterm, dungeonf7autotermclickdelay, resurrectionItemTriggeredTitle, dungeonAutoCloseChest, dungeonPuzzleHelper, dungeonf7msgbot, dungeonf7msgbotsimonsaysstart, dungeonf7msgbotsimonsays1, dungeonf7msgbotsimonsays2, dungeonf7msgbotsimonsays3, dungeonf7msgbotsimonsays4, dungeonf7msgbotsimonsays5, dungeonf7msgbotmelodystart, dungeonf7msgbotmelody1, dungeonf7msgbotmelody2, dungeonf7msgbotmelody3, dungeonf7msgbotmelody4, dungeonf7msgbotcoretunnel, dungeonBonzoTriggered, dungeonPhoenixTriggered, dungeonSpiritMaskTriggered)),
            Map.entry("rift", List.of(rifttimegunhelper)),
            Map.entry("界面类", List.of(bossbar, bossbarShowHealth, bossbarAddTargetEntity, bossbarDisplayLimit, dyingtip, noblind, nosuffoverlay, fireOverlay))
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
            if (value instanceof Boolean value2) {
                jo.addProperty(option.getName(), value2);
            } else if (value instanceof Integer value2) {
                jo.addProperty(option.getName(), value2);
            } else if (value instanceof String value2) {
                jo.addProperty(option.getName(), value2);
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

    public static final File capeFolder;
    public static final File capeFile;

    static {
        capeFolder = new File(config_folder, "customcape");
        capeFolder.mkdirs();
        capeFile = new File(capeFolder, "cape.png");
    }

}
