package pers.XiaoShadiao.skydiao.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * QuickNav 按钮配置。
 *
 * <p>图标支持三种写法：
 * <ul>
 *   <li>{@code minecraft:gold_block} + 可选 components</li>
 *   <li>{@code skyblock:HYPERION}（从 NEU 索引解析）</li>
 *   <li>{@code head:&lt;base64&gt;}（直接使用玩家头颅纹理）</li>
 * </ul>
 */
public final class QuickNavConfig {

    private static final Logger log = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public List<Entry> buttons = new ArrayList<>();

    public static Path path() {
        return ConfigManager.config_folder.toPath().resolve("quicknav.json");
    }

    public static QuickNavConfig load() {
        Path path = path();
        if (!Files.exists(path)) {
            QuickNavConfig defaults = defaults();
            defaults.save();
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            QuickNavConfig config = GSON.fromJson(reader, QuickNavConfig.class);
            if (config == null || config.buttons == null || config.buttons.isEmpty()) {
                return defaults();
            }
            config.buttons.removeIf(Objects::isNull);
            if (config.buttons.isEmpty()) {
                return defaults();
            }
            return config;
        } catch (Exception e) {
            log.warn("读取 QuickNav 配置失败: {}", path, e);
            return defaults();
        }
    }

    public void save() {
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("保存 QuickNav 配置失败", e);
        }
    }

    public static QuickNavConfig defaults() {
        QuickNavConfig config = new QuickNavConfig();
        config.buttons.add(new Entry("minecraft:diamond_sword", "", "/skills", "Skills", "Your Skills", false));
        config.buttons.add(new Entry("minecraft:painting", "", "/collection", "Collections", "Collections", false));
        config.buttons.add(new Entry("minecraft:bone", "", "/pets", "Pets", "(?:\\(\\d+/\\d+\\) )?Pets", false));
        config.buttons.add(new Entry("minecraft:leather_chestplate", "[minecraft:dyed_color=8991416]",
                "/armor", "Armor Sets", "\\(\\d+/\\d+\\) Armor Sets", false));
        config.buttons.add(head("", "-2081424676,-57521078,-2073572414,158072763",
                "ewogICJ0aW1lc3RhbXAiIDogMTU5MTMxMDU4NTYwOSwKICAicHJvZmlsZUlkIiA6ICI0MWQzYWJjMmQ3NDk0MDBjOTA5MGQ1NDM0ZDAzODMxYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZWdha2xvb24iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODBhMDc3ZTI0OGQxNDI3NzJlYTgwMDg2NGY4YzU3OGI5ZDM2ODg1YjI5ZGFmODM2YjY0YTcwNjg4MmI2ZWMxMCIKICAgIH0KICB9Cn0=",
                "/sacks", "Sacks", "Sack of Sacks", false));
        config.buttons.add(head("5da6bec64bd942bc", "1571208902,1272529596,-1566400349,-679283814",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYxYTkxOGMwYzQ5YmE4ZDA1M2U1MjJjYjkxYWJjNzQ2ODkzNjdiNGQ4YWEwNmJmYzFiYTkxNTQ3MzA5ODVmZiJ9fX0=",
                "/accessories", "Accessories", "(?:\\(\\d+/\\d+\\) )?Accessory Bag", false));
        config.buttons.add(new Entry("minecraft:ender_chest", "", "/storage", "Storage",
                "(?:Rift )?Storage(?: \\(\\d/\\d\\))?", false));
        config.buttons.add(head("421a8ef40eff47f4", "1109036788,251611124,-2126904485,-130621758",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzljODg4MWU0MjkxNWE5ZDI5YmI2MWExNmZiMjZkMDU5OTEzMjA0ZDI2NWRmNWI0MzliM2Q3OTJhY2Q1NiJ9fX0=",
                "/is", "Home", null, true));
        config.buttons.add(head("e30e30d02878417c", "-485609264,678969724,-1929747597,-718202427",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjQ4ODBkMmMxZTdiODZlODc1MjJlMjA4ODI2NTZmNDViYWZkNDJmOTQ5MzJiMmM1ZTBkNmVjYWE0OTBjYjRjIn19fQ==",
                "/warp garden", "Garden", null, true));
        config.buttons.add(head("", "-300151517,-631415889,-1193921967,-1821784279",
                "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDdjYzY2ODc0MjNkMDU3MGQ1NTZhYzUzZTA2NzZjYjU2M2JiZGQ5NzE3Y2Q4MjY5YmRlYmVkNmY2ZDRlN2JmOCJ9fX0=",
                "/hub", "Skyblock Hub", null, true));
        config.buttons.add(head("", "1605800870,415127827,-1236127084,15358548",
                "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzg5MWQ1YjI3M2ZmMGJjNTBjOTYwYjJjZDg2ZWVmMWM0MGExYjk0MDMyYWU3MWU3NTQ3NWE1NjhhODI1NzQyMSJ9fX0=",
                "/warp dungeon_hub", "Dungeons Hub", null, true));
        config.buttons.add(new Entry("minecraft:gold_block", "", "/ah", "Auction House",
                "^(?:Auctions Browser|Co-op Auction House|Auction House|Manage Auctions|Your Bids|BIN Auction View|Auction View|Auctions:.*)$", false));
        config.buttons.add(head("", "-562285948,532499670,-1705302742,775653035",
                "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmZlMmRjZGE0MWVjM2FmZjhhZjUwZjI3MmVjMmUwNmE4ZjUwOWUwZjgwN2YyMzU1YTFmNWEzM2MxYjY2ZTliNCJ9fX0=",
                "/bz", "Bazaar", "(?:Co-op )?Bazaar .*", false));
        config.buttons.add(new Entry("minecraft:crafting_table", "", "/craft", "Crafting Table", "Craft Item", false));
        return config;
    }

    private static Entry head(String name, String id, String texture, String command,
                              String tooltip, String titleRegex, boolean doubleClick) {
        String components = "[minecraft:profile={name:\"" + name
                + "\",id:[I;" + id
                + "],properties:[{name:\"textures\",value:\"" + texture + "\"}]}]";
        return new Entry("minecraft:player_head", components, command, tooltip, titleRegex, doubleClick);
    }

    public static final class Entry {
        public String icon = "";
        public String components = "";
        public String command = "";
        public String tooltip = "";
        public String titleRegex;
        public boolean doubleClick;

        public Entry() {
        }

        public Entry(String icon, String components, String command, String tooltip,
                     String titleRegex, boolean doubleClick) {
            this.icon = icon;
            this.components = components;
            this.command = command;
            this.tooltip = tooltip;
            this.titleRegex = titleRegex;
            this.doubleClick = doubleClick;
        }
    }
}
