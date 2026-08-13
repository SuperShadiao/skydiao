package pers.XiaoShadiao.skydiao.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.appendage.utils.posrecord.RecordPos;
import pers.XiaoShadiao.skydiao.config.option.*;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.bilibili.BLiveListener;
import pers.XiaoShadiao.skydiao.screen.HudConfigScreen;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConfigManager {

    private static final Logger log = LogManager.getLogger();

    public static final File config_folder = Paths.get(ToolList.mc.gameDirectory.getPath(), "config", "小沙雕_config", "skydiao").toFile();
    public static final File configFile = getCustomConfigFileName("config.json");

    public static final File configResetFlag = getCustomConfigFileName("configResetFlag.json");

    public static File getCustomConfigFileName(String fileName) {
        return new File(ConfigManager.config_folder, fileName);
    }

    public static final BooleanConfigOption blivelistener = new BooleanConfigOption("blivelistener", false) {
        @Override
        public void setValue(Boolean value) {
            if (value) {
                BLiveListener.launch();
            }
            super.setValue(value);
        }
    };
    public static final StringConfigOption blivelistenercode = new StringConfigOption("blivelistenercode", "");
    public static final BooleanConfigOption cooltitle = new BooleanConfigOption("cooltitle", true) {
        @Override
        public void setValue(Boolean value) {
            if (!value && ToolList.mc.getWindow() != null) ToolList.mc.updateTitle();
            super.setValue(value);
        }
    };
    public static final StringConfigOption customTitleText = new StringConfigOption("customtitletext", "");

    public static final ColorConfigOption lyricColor1 = new ColorConfigOption("lyriccolor1", new Color(0x00, 0xFF, 0x00).getRGB());
    public static final ColorConfigOption lyricColor2 = new ColorConfigOption("lyriccolor2", new Color(0xFF, 0xFF, 0xFF).getRGB());

    public static final BooleanConfigOption mineshaftSharing = new BooleanConfigOption("skyblockmineshaftsharing", true);
    public static final BooleanConfigOption autogg = new BooleanConfigOption("autogg", true);
    public static final SelectConfigOption language = new SelectConfigOption("language", CrowdinI18nManager.fromSystemLanguage().ordinal(), Arrays.stream(CrowdinI18nManager.LangCode.values()).map(v -> v.displayName).toList());
    public static final BooleanConfigOption dungeonRenderDangerousEnemy = new BooleanConfigOption("skyblockdungeonenemydisplay", true);
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
    public static final TimeDelayOption autofishrethrowhookdelay = new TimeDelayOption("autofishrethrowhookdelay", 0).flagAsMacroFeature();
    public static final BooleanConfigOption pfAllowBreak = new BooleanConfigOption("pathfinderallowbreak", false);
    public static final BooleanConfigOption pfAllowPlace = new BooleanConfigOption("pathfinderallowplace", false);
    public static final BooleanConfigOption pfStopWhenTP = new BooleanConfigOption("pathfinderstopwhentp", false);
    public static final IntConfigOption pfTimeout = new IntConfigOption("pathfindertimeout", 60000);
    public static final BooleanConfigOption pathfinderallowbreakwhengetslowmining = new BooleanConfigOption("pathfinderallowbreakwhengetslowmining", false);
    public static final BooleanConfigOption pfXRay = new BooleanConfigOption("pathfinderxray", false);
    public static final BooleanConfigOption autoDojo = new BooleanConfigOption("autodojo", false);
    public static final IntConfigOption autoDojoControlPredictDist = new IntConfigOption("autodojocontrolpredictdist", 8);
    public static final SelectConfigOption autoDojoMasteryShootTiming = new SelectConfigOption("autodojomasteryshoottiming", 2, List.of("0.0s", "0.1s", "0.2s", "0.3s", "0.4s", "0.5s", "0.6s", "0.7s", "0.8s", "0.9s"));
    public static final BooleanConfigOption necronLadderNotification = new BooleanConfigOption("necronladdernotification", true);
    public static final BooleanConfigOption dungeonf7autoterm = new BooleanConfigOption("dungeonf7autoterm", false);
    public static final BooleanConfigOption enablexsdccommandtip = new BooleanConfigOption("enablexsdccommandtip", true);
    public static final BooleanConfigOption enableircjointip = new BooleanConfigOption("enableircjointip", true);
    public static final BooleanConfigOption enableircafktip = new BooleanConfigOption("enableircafktip", true);
    public static final BooleanConfigOption enableircmacrochecktip = new BooleanConfigOption("enableircmacrochecktip", true);
    public static final SelectConfigOption fireOverlay = new SelectConfigOption("fireoverlay", 1, List.of("config.fireoverlay.options.normal", "config.fireoverlay.options.lower", "config.fireoverlay.options.remove"));
    public static final BooleanConfigOption skydiaocustomcape = new BooleanConfigOption("skydiaocustomcape", false);
    public static final BooleanConfigOption skyblockriftautodanceroom = new BooleanConfigOption("skyblockriftautodanceroom", false);
    public static final BooleanConfigOption mineshaftHelper = new BooleanConfigOption("mineshafthelper", true);
    public static final BooleanConfigOption galateashulker = new BooleanConfigOption("galateashulker", true);
    public static final TimeDelayOption dungeonf7autotermclickdelay = new TimeDelayOption("dungeonf7autotermclickdelay", 270);
    public static final BooleanConfigOption rifttimegunhelper = new BooleanConfigOption("rifttimegunhelper", false);
    public static final BooleanConfigOption slayerTogether = new BooleanConfigOption("slayertogether", true);
    public static final BooleanConfigOption resurrectionItemTriggeredTitle = new BooleanConfigOption("resurrectionitemtriggeredtitle", true);
    public static final BooleanConfigOption hubratesp = new BooleanConfigOption("hubratesp", true);
    public static final BooleanConfigOption autoSprayonator = new BooleanConfigOption("fsautosprayonator", false).flagAsMacroFeature();
    public static final StringConfigOption autoKillPests = new StringConfigOption("fsautokillpests", "").flagAsMacroFeature();
    public static final BooleanConfigOption halfAutoKillPests = new BooleanConfigOption("fshalfautokillpests", false).flagAsMacroFeature();
    public static final StringConfigOption autoChangeLo = new StringConfigOption("fsautochangelo", "").flagAsMacroFeature();
    public static final BooleanConfigOption gardenTrapPrompt = new BooleanConfigOption("gardentrapprompt", true);
    public static final BooleanConfigOption gardenBonusPrompt = new BooleanConfigOption("gardenbonusprompt", true);
    public static final BooleanConfigOption fsGardenMoonFlowerMode = new BooleanConfigOption("fsgardenmoonflowermode", true).flagAsMacroFeature();
    public static final BooleanConfigOption fsGardenMoonFlowerModeKeepNightFarming = new BooleanConfigOption("fsgardenmoonflowermodekeepnightfarming", true).flagAsMacroFeature();
    public static final BooleanConfigOption hotspotrender = new BooleanConfigOption("hotspotrender", true);
    public static final BooleanConfigOption dungeonAutoCloseChest = new BooleanConfigOption("dungeonautoclosechest", false);
    public static final BooleanConfigOption dungeonPuzzleHelper = new BooleanConfigOption("dungeonpuzzlehelper", true);
    public static final BooleanConfigOption lotusAtollHelper = new BooleanConfigOption("lotusatollhelper", true);
    public static final BooleanConfigOption lotusAtollAutofishKeep = new BooleanConfigOption("lotusatollautofishkeepjump", true);
    public static final TimeDelayOption autofishDelayRetraction = new TimeDelayOption("autofishdelayretraction", 0);
    public static final BooleanConfigOption fishingBigFishRender = new BooleanConfigOption("fishingbigfishrender", true);
    public static final StringConfigOption fishingBigFishTip = new StringConfigOption("fishingbigfishtip", "&e一只肥大的鱼出现了!");
    public static final BooleanConfigOption blivemodetab = new BooleanConfigOption("blivemodetab", false);
    public static final BooleanConfigOption blivemodeentityname = new BooleanConfigOption("blivemodeentityname", false);
    public static final BooleanConfigOption blivemodechat = new BooleanConfigOption("blivemodechat", false);
    public static final BooleanConfigOption blivemodehideserverid = new BooleanConfigOption("blivemodehideserverid", false);
    public static final BooleanConfigOption carnivalAutoFruitDigger = new BooleanConfigOption("carnivalfruitdigger", false).flagAsMacroFeature();
    public static final BooleanConfigOption keepSprint = new BooleanConfigOption("sprint", true) {
        @Override
        public void setValue(Boolean value) {
            if (!value) InputSimulator.setSprint(false);
            super.setValue(value);
        }
    };
    public static final BooleanConfigOption crystalHollowHelper = new BooleanConfigOption("crystalhollowhelper", true);
    public static final BooleanConfigOption crystalHollowHelperDebug = new BooleanConfigOption("crystalhollowhelperdebug", false);
    public static final BooleanConfigOption crystalHollowDupServerTipper = new BooleanConfigOption("crystalhollowdupservertipper", true);
    public static final StringConfigOption mineshaftShareAnnounce = new StringConfigOption("mineshaftshareannounce", "");
    public static final BooleanConfigOption skyblockautobloodfiend = new BooleanConfigOption("skyblockautobloodfiend", false).flagAsMacroFeature();
    public static final IntConfigOption skyblockautobloodfiendlowhealth = new IntConfigOption("skyblockautobloodfiendlowhealth", 5).flagAsMacroFeature();
    public static final BooleanConfigOption crystalHollowHelperDisableThreadLimit = new BooleanConfigOption("crystalhollowhelperdisablethreadlimit", true) {
        @Override
        public void setValue(Boolean value) {
            super.setValue(value);
            ToolList.mc.schedule(AbstractListener.crystalHollowHelperListener::updateThreadLimit);
        }
    };
    public static final BooleanConfigOption dungeonf7InactiveTerminalRender = new BooleanConfigOption("skyblockdungeonf7inactiveterminaldisplay", true);
    public static final BooleanConfigOption genshinImpactHeatColdRender = new BooleanConfigOption("genshinimpactheatcoldrender", true);
    public static final BooleanConfigOption autoReconnect = new BooleanConfigOption("autoreconnect", true);
    public static final BooleanConfigOption afkInOtherPlace = new BooleanConfigOption("afkinotherplace", true);
    public static final DoubleConfigOption freecamFlySpeed = new DoubleConfigOption("freecamflyspeed", 1.0);
    public static final BooleanConfigOption ravengardHelper = new BooleanConfigOption("ravengardhelper", true);
    public static final BooleanConfigOption dungeonKeyRender = new BooleanConfigOption("dungeonkeyrender", true);
    public static final BooleanConfigOption autoReel = new BooleanConfigOption("autoreel", false);
    public static final BooleanConfigOption autoReelAutoAim = new BooleanConfigOption("autoreelautoaim", false);
    public static final BooleanConfigOption torrhusCanyonHelper = new BooleanConfigOption("torrhuscanyonhelper", true);
    public static final BooleanConfigOption safariBoardcastHotspot = new BooleanConfigOption("safariboardcasthotspot", true);
    public static final BooleanConfigOption safariBoardcastTradeNPC = new BooleanConfigOption("safariboardcasttradenpc", true);
    public static final BooleanConfigOption safariRenderTargetESP = new BooleanConfigOption("safaritargetrenderepesp", true);
    public static final BooleanConfigOption floorDroppingRender = new BooleanConfigOption("floordroppingrender", true);
    public static final BooleanConfigOption dungeonReviveItemCDRender = new BooleanConfigOption("dungeonreviveitemcdrender", true);
    public static final BooleanConfigOption isleVolcanoFinder =  new BooleanConfigOption("islevolcanofinder", true);
    public static final BooleanConfigOption isleVolcanoFinderCataOnlyMode = new BooleanConfigOption("islevolcanofindercataonlymode", true);
    public static final BooleanConfigOption isleDupServerTip = new BooleanConfigOption("isledupservertip", true);
    public static final BooleanConfigOption miningCommissionEntityESP = new BooleanConfigOption("miningcommissionentityespesp", true);
    public static final BooleanConfigOption fsTPToPest = new BooleanConfigOption("fstopopest", true);

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
    public static final StringConfigOption dungeonBloodRoomTime = new StringConfigOption("dungeonbloodroomtime", "本次使用了[time]s肘击到血房! 咕咕嘎嘎!");
    public static final StringConfigOption dungeonDrinkPotion = new StringConfigOption("dungeondrinkpotion", "能量饮料真好喝! 我为Dungeon Potion代言!");
    public static final StringConfigOption dungeonTrashTPS = new StringConfigOption("dungeontrashtps", "太强了喵, 我们服务器有很高的[tps] TPS喵!");
    public static final StringConfigOption dungeonf7msgbotssleap = new StringConfigOption("dungeonf7msgbotssleap", "已安全到达Simon Says处, 准许Leap!");

    public static final StringConfigOption hudOffsetAndScale = new StringConfigOption("hudoffsetandscale", "{}");

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

    public static final BooleanConfigOption macroReplay = new BooleanConfigOption("macroreplay", true);

    public static final BooleanConfigOption chatbutton = new BooleanConfigOption("chatbutton", false).setRequiredMod(new ConfigOption.ModDepends("chatpatches", "alpha.8", "https://modrinth.com/mod/chatpatches"));

    public static final StringConfigOption hypixelhelpermusicfolder = new StringConfigOption("hypixelhelpermusicfolder", "");
    public static final BooleanConfigOption musicplayer = new BooleanConfigOption("musicplayer", true);
    public static final SelectConfigOption musicplayermode = new SelectConfigOption("musicplayermode", 0, List.of("顺序播放", "有序随机", "无序随机", "while(true)"));
    public static final IntConfigOption musiclastmusic = new IntConfigOption("musiclastmusic", 0);
    public static final IntConfigOption xsdmusicvolume = new IntConfigOption("xsdmusicvolume", 100);


    public static final Function<String, Runnable> openChat = (msg) -> () -> ToolList.mc.setScreenAndShow(new ChatScreen(msg, false, true));
    public static final Runnable openControl = () -> ToolList.mc.setScreenAndShow(new KeyBindsScreen(ToolList.mc.screen, ToolList.mc.options));

    public static final ActionConfigOption openFsCommandAction = new ActionConfigOption("openfscommandaction", openChat.apply("/skydiaofs"));
    public static final ActionConfigOption openFreelookAndFreecamAction = new ActionConfigOption("openfreelookandfreecamaction", openControl);
    public static final ActionConfigOption openAutoClickAction = new ActionConfigOption("openautoclickaction", openChat.apply("/skydiao autoclick"));
    public static final ActionConfigOption openFsKeyBind = new ActionConfigOption("openfskeybind", openControl);
    public static final ActionConfigOption openClearStashCommand = new ActionConfigOption("openclearstashcommand", openChat.apply("/skydiao fastclearminingstash"));
    public static final ActionConfigOption openHud = new ActionConfigOption("openhud", () -> ToolList.mc.setScreenAndShow(new HudConfigScreen(ToolList.mc.screen)));

    public static final BooleanConfigOption iAutoObsidian = new BooleanConfigOption("iautoobsidian", false) {
        @Override
        public void setValue(Boolean value) {
            if(value) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e注意: 此功能非小沙雕编写, 未确定安全性, 请谨慎使用."));
            super.setValue(value);
        }
    }.flagAsMacroFeature();
    public static final BooleanConfigOption iAutoObsidianWR = new BooleanConfigOption("iautoobsidianwithretry", false) {
        @Override
        public void setValue(Boolean value) {
            if(value) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e注意: 此功能非小沙雕编写, 未确定安全性, 请谨慎使用."));
            super.setValue(value);
        }
    }.flagAsMacroFeature();

    public static final ActionConfigOption obsidianPositionHelper = new ActionConfigOption("obsidianpositionhelper", openChat.apply("/apd recpos help"));
    public static final StringGetterSelectConfigOption autoObsidianPositionsFile = new StringGetterSelectConfigOption("autoobsidianpositionsfile","", RecordPos::getPListFileNames);
    public static final IntConfigOption drillSlot = new IntConfigOption("drillslot", 7);
    public static final IntConfigOption lanternSlot = new IntConfigOption("lanternslot", 6);
    public static final IntConfigOption obsidianPlayerCheckRange = new IntConfigOption("obsidianplayercheckrange", 15);
    public static final BooleanConfigOption obsidianPlayerCheck = new BooleanConfigOption("obsidianplayercheck", true);
    public static final BooleanConfigOption obsidianTheEndCheck = new BooleanConfigOption("obsidiantheendcheck", true);
    public static final IntConfigOption obsidianCycleTicks = new IntConfigOption("obsidiancycleticks", 9);

    public static final List<Map.Entry<String, List<ConfigOption<?>>>> categories = List.of(
            Map.entry("basic", List.of(language, enablexsdccommandtip, enableircjointip, enableircafktip, enableircmacrochecktip, cooltitle, customTitleText)),
            Map.entry("工具类", List.of(inventoryFilter, chatbutton, skydiaocustomcape, blivelistener, blivelistenercode, blivemodetab, blivemodeentityname, blivemodechat, blivemodehideserverid, keepSprint, autoReconnect, afkInOtherPlace, freecamFlySpeed, openFreelookAndFreecamAction, openAutoClickAction, openClearStashCommand)),
            Map.entry("寻路系统", List.of(pfAllowBreak, pfAllowPlace, pfStopWhenTP, pfTimeout, pathfinderallowbreakwhengetslowmining, pfXRay)),
            Map.entry("自动类", List.of(macroReplay, autoEnchantTableGame, autoHarp, autoFish, autoFishAutoJump, autoFishAutoMove, autoFishAutoRotation, lotusAtollAutofishKeep, autofishrethrowhookdelay, autofishDelayRetraction, autoDojo, autoDojoControlPredictDist, autoDojoMasteryShootTiming, skyblockriftautodanceroom, carnivalAutoFruitDigger, skyblockautobloodfiend, skyblockautobloodfiendlowhealth, iAutoObsidian, iAutoObsidianWR, obsidianPositionHelper, autoObsidianPositionsFile, drillSlot, lanternSlot, obsidianPlayerCheckRange, obsidianPlayerCheck, obsidianTheEndCheck, obsidianCycleTicks)),
            Map.entry("mining", List.of(mineshaftHelper, mineshaftSharing, mineshaftShareAnnounce, skyblockSafeIsland, crystalHollowHelper, crystalHollowHelperDebug, crystalHollowDupServerTipper, crystalHollowHelperDisableThreadLimit, genshinImpactHeatColdRender, miningCommissionEntityESP)),
            Map.entry("combat", List.of(slayerTogether, isleVolcanoFinder, isleVolcanoFinderCataOnlyMode, isleDupServerTip)),
            Map.entry("foraging", List.of(galateashulker, autoReel, autoReelAutoAim, torrhusCanyonHelper, safariBoardcastHotspot, safariBoardcastTradeNPC, safariRenderTargetESP, floorDroppingRender)),
            Map.entry("farming", List.of(openFsCommandAction, openFsKeyBind, hubratesp, autoSprayonator, autoChangeLo, halfAutoKillPests, gardenTrapPrompt, gardenBonusPrompt, fsGardenMoonFlowerMode, fsGardenMoonFlowerModeKeepNightFarming, fsTPToPest)),
            Map.entry("fishing", List.of(hotspotrender, autogg, lotusAtollHelper, fishingBigFishRender, fishingBigFishTip)),
            Map.entry("dungeon", List.of(dungeonRenderDangerousEnemy, dungeonKeyRender, dungeonRenderTraps, necronLadderNotification, dungeonf7autoterm, dungeonf7autotermclickdelay, resurrectionItemTriggeredTitle, dungeonAutoCloseChest, dungeonPuzzleHelper, dungeonf7InactiveTerminalRender, dungeonReviveItemCDRender, dungeonf7msgbot, dungeonf7msgbotsimonsaysstart, dungeonf7msgbotsimonsays1, dungeonf7msgbotsimonsays2, dungeonf7msgbotsimonsays3, dungeonf7msgbotsimonsays4, dungeonf7msgbotsimonsays5, dungeonf7msgbotmelodystart, dungeonf7msgbotmelody1, dungeonf7msgbotmelody2, dungeonf7msgbotmelody3, dungeonf7msgbotmelody4, dungeonf7msgbotcoretunnel, dungeonBonzoTriggered, dungeonPhoenixTriggered, dungeonSpiritMaskTriggered, dungeonDrinkPotion, dungeonBloodRoomTime, dungeonTrashTPS, dungeonf7msgbotssleap)),
            Map.entry("rift", List.of(rifttimegunhelper)),
            Map.entry("界面类", List.of(openHud, bossbar, bossbarShowHealth, bossbarAddTargetEntity, bossbarDisplayLimit, dyingtip, noblind, nosuffoverlay, fireOverlay, lyricColor1, lyricColor2)),
            Map.entry("ravengard", List.of(ravengardHelper))
    );


    /* ==================init================== */

    public static final List<ConfigOption<?>> optionList = Arrays.stream(ConfigManager.class.getDeclaredFields()).filter(field -> ConfigOption.class.isAssignableFrom(field.getType())).map(field -> {
        try {
            field.setAccessible(true);
            return Objects.requireNonNull((ConfigOption<?>) field.get(null), field.getName());
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException("Error getting config option", e);
        }
    }).collect(Collectors.toList());

    public static void saveConfig() {
        JsonObject jo = new JsonObject();
        for (ConfigOption<?> option : optionList) {
            if (option instanceof ActionConfigOption) {
                continue;
            }
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

    private static final Int2ObjectMap<List<ConfigOption<?>>> tryToResetToDefaultMap = Int2ObjectMap.ofEntries(
            Int2ObjectMap.entry(0, List.of(bossbar, bossbarShowHealth, bossbarAddTargetEntity)),
            Int2ObjectMap.entry(1, List.of(dungeonRenderDangerousEnemy, dungeonKeyRender))
    );

    static {
        readConfig();
    }

    private static void readConfig() {
        try {
            JsonObject jo = JsonParser.parseString(FileUtils.readFileToString(configFile, StandardCharsets.UTF_8)).getAsJsonObject();
            for (ConfigOption<?> option : optionList) {
                if (jo.has(option.getName())) {
                    try {
                        if (option instanceof BooleanConfigOption booleanOption) {
                            booleanOption.setValue(jo.get(option.getName()).getAsBoolean());
                        } else if (option instanceof SelectConfigOption selectOption) {
                            selectOption.setValue(jo.get(option.getName()).getAsInt());
                        } else if (option instanceof StringConfigOption stringOption) {
                            stringOption.setValue(jo.get(option.getName()).getAsString());
                        } else if (option instanceof IntConfigOption intgOption) {
                            intgOption.setValue(jo.get(option.getName()).getAsInt());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (resetToDefault()) {
                saveConfig();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static final File capeFolder;
    public static final File capeFile;

    static {
        capeFolder = getCustomConfigFileName("customcape");
        capeFolder.mkdirs();
        capeFile = new File(capeFolder, "cape.png");

        checkDisabledConfigs();
    }

    private static boolean resetToDefault() {
        JsonArray ja;
        try {
            if (!configResetFlag.exists()) {
                configResetFlag.createNewFile();
                ja = new JsonArray();
            } else {
                ja = JsonParser.parseString(FileUtils.readFileToString(configResetFlag, StandardCharsets.UTF_8)).getAsJsonArray();
            }
            for (JsonElement je : ja) {
                je.getAsInt();
            }
        } catch (Exception e) {
            e.printStackTrace();
            ja = new JsonArray();
        }

        Int2ObjectMap<List<ConfigOption<?>>> temp = new Int2ObjectOpenHashMap<>(tryToResetToDefaultMap);
        for (JsonElement je : ja) {
            temp.remove(je.getAsInt());
        }
        boolean flag = false;
        for (Int2ObjectMap.Entry<List<ConfigOption<?>>> entry : temp.int2ObjectEntrySet()) {
            flag = true;
            ja.add(entry.getIntKey());
            entry.getValue().forEach(ConfigOption::resetToDefault);
        }
        try {
            FileUtils.writeStringToFile(configResetFlag, ja.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return flag;
    }

    private static void checkDisabledConfigs() {
        Thread thread = Thread.currentThread();
        FutureTask<Void> task = new FutureTask<>(() -> {

            int sleepTime = 5;
            while (true) {
                try(InputStream is = ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club/skydiaoDisabledFeatures.json")) {
                    String result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    Iterator<JsonElement> it = JsonParser.parseString(result).getAsJsonArray().iterator();

                    List<String> list = new ArrayList<>();
                    while (it.hasNext()) {
                        JsonElement next = it.next();
                        list.add(next.getAsString());
                    }

                    setDisabledFeatures(list);
                    break;
                } catch (IOException e) {
                    log.error("Can't get disabled features");
                    log.catching(e);
                    if(sleepTime > 0) {
                        sleepTime--;
                        if(sleepTime == 0) {
                            thread.interrupt();
                        }
                        continue;
                    }
                    Thread.sleep(60000);
                }
            }
            return null;
        });

        new Thread(task, "Disabled Features Checker").start();
        try {
            task.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Check disabled features timed out", e);
        }
    }

    private static List<String> disabledFeatures = Collections.emptyList();

    public static void setDisabledFeatures(List<String> list) {
        disabledFeatures = Collections.unmodifiableList(list);
    }

    public static boolean isForceDisabled(String name) {
        return disabledFeatures.contains(name);
    }

    public static boolean isForceDisabled(ConfigOption<?> option) {
        return disabledFeatures.contains(option.getName());
    }

}
