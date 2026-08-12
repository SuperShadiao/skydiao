package pers.XiaoShadiao.skydiao.utils.tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class TabReader {

    private static List<String> tabLines = new ArrayList<>();

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
    }

    // 获取整行文本列表
    public static List<String> getLines() {
        return tabLines == null ? List.of() : tabLines;
    }

    public static Optional<String> findLineStartsWith(String prefix) {
        refreshTab();
        for (String line : getLines()) {
            if (line.startsWith(prefix)) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> findLineWith(String regex) {
        refreshTab();
        Pattern pattern = Pattern.compile(regex);
        for (String line : getLines()) {
            if (pattern.matcher(line).find()) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }
}
