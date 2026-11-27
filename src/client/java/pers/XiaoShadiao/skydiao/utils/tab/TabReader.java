package pers.XiaoShadiao.skydiao.utils.tab;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

public class TabReader {

    private static final Comparator<PlayerInfo> TAB_ORDER = Comparator
            .comparingInt(PlayerInfo::getTabListOrder).reversed()
            .thenComparing(info -> info.getGameMode() == GameType.SPECTATOR)
            .thenComparing(info -> info.getTeam() == null ? "" : info.getTeam().getName())
            .thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);

    private static List<String> tabLines = new ArrayList<>();
    private static List<String> tabNoColorLines = new ArrayList<>();

    public static void refreshTab() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            tabLines = null;
            return;
        }

        // 把集合转为ArrayList
        List<PlayerInfo> onlinePlayers = new ArrayList<>(connection.getOnlinePlayers());

        tabLines = onlinePlayers.stream()
                .map(PlayerInfo::getTabListDisplayName)
                .filter(Objects::nonNull)
                .map(Component::getString)
                .map(String::trim)
                .toList();
        tabNoColorLines = tabLines.stream().map(ToolList.getInstance()::deleteColorCode).toList();
    }

    // 保留 Tab 的行顺序和缩进，用于读取多行组件。
    public static List<String> getOrderedNoColorLines() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();

        return connection.getListedOnlinePlayers().stream()
                .sorted(TAB_ORDER)
                .map(PlayerInfo::getTabListDisplayName)
                .map(component -> component == null ? "" : ToolList.getInstance().deleteColorCode(component.getString()))
                .toList();
    }

    // 获取整行文本列表
    public static List<String> getLines() {
        return tabLines == null ? List.of() : tabLines;
    }

    // 获取无颜色的整行文本列表
    public static List<String> getNoColorLines() {
        return tabNoColorLines == null ? List.of() : tabNoColorLines;
    }

    public static Optional<String> findLineStartsWith(String prefix) {
        for (String line : getLines()) {
            if (line.startsWith(prefix)) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    private static final Cache<String, Pattern> regex2PatternCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(Duration.ofMinutes(5))
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .build();

    public static Optional<String> findLineWith(String regex) {
        try {
            Pattern pattern = regex2PatternCache.get(regex, () -> Pattern.compile(regex));
            for (String line : getLines()) {
                if (pattern.matcher(line).find()) {
                    return Optional.of(line);
                }
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }

    public static void clearCache() {
        tabLines = null;
        tabNoColorLines = null;
    }

    public static Optional<String> findNoColorLineStartsWith(String prefix) {
        for (String line : getNoColorLines()) {
            if (line.startsWith(prefix)) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> findNoColorLineWith(String regex) {
        try {
            Pattern pattern = regex2PatternCache.get(regex, () -> Pattern.compile(regex));
            for (String line : getNoColorLines()) {
                if (pattern.matcher(line).find()) {
                    return Optional.of(line);
                }
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }
}
