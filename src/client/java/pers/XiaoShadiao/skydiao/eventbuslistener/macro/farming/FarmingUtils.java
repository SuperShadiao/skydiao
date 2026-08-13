package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static pers.XiaoShadiao.skydiao.utils.ToolList.mc;

public class FarmingUtils {
    public static @NonNull Map<String, Integer> getFarmingToolIndex() {
        Map<String, Integer> farmingToolIndex = new HashMap<>();
        Inventory inventory = mc.player.getInventory();
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack stack = inventory.getItem(hotbarSlot);
            if (stack.isEmpty()) continue;
            String itemName = stack.getHoverName().getString().toLowerCase();
            if (itemName.contains("vacuum")) farmingToolIndex.put("vacuum", hotbarSlot);
            else if (itemName.contains("sprayonator")) farmingToolIndex.put("sprayonator", hotbarSlot);
        }
        if (!farmingToolIndex.containsKey("vacuum"))
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c没有在快捷栏找到vacuum，不会自动杀虫"));
        if (!farmingToolIndex.containsKey("sprayonator"))
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c没有在快捷栏找到sprayonator，不会自动喷药"));
        return farmingToolIndex;
    }

    public static ArrayList<Integer> strConfToIntArr(String config, String errorMsg, int wantCount) {
        if (config == null || config.isEmpty()) return null;
        try {
            ArrayList<Integer> collect = Arrays.stream(config.split("[,，]"))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Integer::valueOf).collect(Collectors.toCollection(ArrayList::new));
            if (collect.size() != wantCount) throw new Exception();
            return collect;
        } catch (Exception e) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c配置错误，无法开启" + errorMsg + "，请按照指引配置"));
            return null;
        }
    }

    private static final Pattern PATTERN = Pattern.compile("(\\d+)([ms])", Pattern.CASE_INSENSITIVE);

    public record ColdDown(boolean ready, int tabTime, int configTime) {}

    public static ColdDown cooldownReady(int readyS) {
        Optional<String> cooldown = TabReader.findLineWith("ColdDown: ");
        if (cooldown.isEmpty()) return new ColdDown(false, 600, readyS);
        String cooldownTime = cooldown.get().substring(cooldown.get().indexOf(":") + 2);
        if (cooldownTime.contains("READY")) return new ColdDown(true, 0, readyS);
        try {
            Matcher matcher = PATTERN.matcher(cooldownTime);
            int m = 0, s = 0;
            while (matcher.find()) {
                int num = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();
                if ("m".equals(unit)) m = num;
                else if ("s".equals(unit)) s = num;
            }
            int total = m * 60 + s;
            return new ColdDown(total < readyS, total, readyS);
        } catch (Exception e) {
            return new ColdDown(false, 600, readyS);
        }
    }

    public static boolean hasPests() {
        return TabReader.findLineWith("Alive: 0").isEmpty() && TabReader.findLineStartsWith("Plots:").map(s -> s.substring(7).split(",").length > 0).orElse(false);
    }

    public static boolean withPet(String petName) {
        return TabReader.findLineWith("\\[Lvl \\d+\\] .*?" + petName).isPresent();
    }

    public static List<String> getPestPlots() {
        return TabReader.findLineStartsWith("Plots:").map(s -> s.substring(7).split(",")).map(Arrays::asList).orElse(List.of());
    }

    public static boolean withPetType(String wait) {
        if ("pest".equals(wait))
            return withPet("Slug") || withPet("Mosquito");
        if ("kpest".equals(wait))
            return withPet("Hedgehog") || withPet("Rose Dragon");
        if ("farm".equals(wait))
            return withPet("Mooshroom Cow") || withPet("Rose Dragon");
        return false;
    }

    public static void changeLoadout(int index) {
        CountDownLatch latch = new CountDownLatch(1);
        AbstractListener.autoLoadoutListener.switchLoadout(index, _ -> latch.countDown());
        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {

        }
    }

    public static void tpToPestPlot() {
        TabReader.findLineStartsWith("Plots:").map(s -> s.substring(7).split(",")).ifPresent(split -> ToolList.sendChatMessage("/tptoplot " + split[0]));
    }
}
