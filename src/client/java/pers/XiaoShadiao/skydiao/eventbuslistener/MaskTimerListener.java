package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaskTimerListener extends AbstractListener {

    public enum MaskKind {
        SPIRIT("Spirit", "spirit_mask", 60, 30 * 20,
                Pattern.compile("^Second Wind Activated! Your Spirit Mask saved your life!$")),
        BONZO("Bonzo", "bonzo_mask", 60, 3 * 60 * 20,
                Pattern.compile("^Your (?:. )?Bonzo's Mask saved your life!$")),

        PHOENIX("Phoenix", "phoenix_pet", 40, 60 * 20,
                Pattern.compile("^Your Phoenix Pet saved you from certain death!$"));

        private final String displayName;
        private final String iconName;
        private final int invincibilityTicks;
        private final int cooldownTicks;
        private final Pattern pattern;

        MaskKind(String displayName, String iconName, int invincibilityTicks, int cooldownTicks, Pattern pattern) {
            this.displayName = displayName;
            this.iconName = iconName;
            this.invincibilityTicks = invincibilityTicks;
            this.cooldownTicks = cooldownTicks;
            this.pattern = pattern;
        }

        public String displayName() {
            return displayName;
        }

        public int invincibilityTicks() {
            return invincibilityTicks;
        }

        public Identifier icon() {
            return Identifier.fromNamespaceAndPath("skydiao", "textures/gui/dynamic_spot/" + iconName + ".png");
        }
    }

    private static final class MaskState {
        private final MaskKind kind;
        private int invincibilityTicks;
        private int cooldownTicks;
        private int totalCooldownTicks;

        private long updateTime;

        private MaskState(MaskKind kind) {
            this.kind = kind;
            this.invincibilityTicks = kind.invincibilityTicks;
            this.cooldownTicks = kind.cooldownTicks;
            this.totalCooldownTicks = kind.cooldownTicks;
            this.updateTime = System.nanoTime();
        }
    }

    public record Row(String name, Identifier icon, String time, boolean active,
                      float cooldownFraction, float invincibilityFraction) {
    }

    private static final Pattern TOOLTIP_COOLDOWN = Pattern.compile("Cooldown: (\\d+)s");

    private static final List<MaskState> STATES = new ArrayList<>();

    @Override
    public String getListenerName() {
        return "MaskTimerListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        ClientTickEvents.START_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    private void onChat(Component component, boolean overlay) {
        MaskKind kind = match(component.getString());
        if (kind != null) trigger(kind);
    }

    public static @Nullable MaskKind match(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) return null;
        String message = ToolList.getInstance().deleteColorCode(rawMessage);
        for (MaskKind kind : MaskKind.values()) {
            if (kind.pattern.matcher(message).matches()) return kind;
        }
        return null;
    }

    public static int remainingTicks(MaskKind kind) {
        for (MaskState state : STATES) {
            if (state.kind == kind) return state.cooldownTicks;
        }
        return 0;
    }

    public static List<Row> rows() {
        List<Row> rows = new ArrayList<>(STATES.size());
        for (MaskState state : STATES) {
            boolean active = state.invincibilityTicks > 0;
            rows.add(new Row(
                    state.kind.displayName,
                    state.kind.icon(),
                    timeText(state),
                    active,
                    Math.clamp((float) state.cooldownTicks / Math.max(1, state.totalCooldownTicks), 0F, 1F),
                    Math.clamp((float) (subTicks(state) / Math.max(1, state.kind.invincibilityTicks)), 0F, 1F)));
        }
        return rows;
    }

    public static List<Row> exampleRows() {
        return List.of(
                new Row("Spirit", MaskKind.SPIRIT.icon(), "IN 2.6s", true, 0.97F, 0.86F),
                new Row("Bonzo", MaskKind.BONZO.icon(), "CD 178s", false, 0.99F, 0F),
                new Row("Phoenix", MaskKind.PHOENIX.icon(), "CD 52s", false, 0.87F, 0F));
    }

    private static void trigger(MaskKind kind) {
        MaskState state = new MaskState(kind);
        if (kind == MaskKind.BONZO) {

            int ticks = readHelmetCooldownTicks();
            if (ticks > 0) {
                state.cooldownTicks = ticks;
                state.totalCooldownTicks = ticks;
            }
        }
        STATES.removeIf(existing -> existing.kind == kind);
        STATES.add(state);
    }

    private static int readHelmetCooldownTicks() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return -1;
        for (Component line : client.player.getItemBySlot(EquipmentSlot.HEAD)
                .getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)) {
            Matcher matcher = TOOLTIP_COOLDOWN.matcher(ToolList.getInstance().deleteColorCode(line.getString()));
            if (matcher.find()) return Integer.parseInt(matcher.group(1)) * 20 + 20;
        }
        return -1;
    }

    private static void tick() {
        if (STATES.isEmpty()) return;
        long now = System.nanoTime();
        Iterator<MaskState> iterator = STATES.iterator();
        while (iterator.hasNext()) {
            MaskState state = iterator.next();
            if (state.invincibilityTicks > 0) state.invincibilityTicks--;
            state.updateTime = now;
            if (--state.cooldownTicks <= 0) iterator.remove();
        }
    }

    private static void clear() {
        STATES.clear();
    }

    private static String timeText(MaskState state) {
        if (state.invincibilityTicks > 0) {
            return String.format(Locale.ROOT, "IN %.1fs", subTicks(state) * 0.05);
        }
        return "CD " + ((state.cooldownTicks + 19) / 20) + "s";
    }

    private static double subTicks(MaskState state) {
        double elapsed = Math.clamp((System.nanoTime() - state.updateTime) / 50_000_000.0, 0.0, 1.0);
        return Math.max(0.0, state.invincibilityTicks - elapsed);
    }
}
