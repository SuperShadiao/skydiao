package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AlertListener extends AbstractListener {

    public enum Kind { SUCCESS, WARNING }

    public record Alert(Kind kind, String title, String subtitle, long createdAt, long durationMs, long removedAt) {
    }

    public record Row(String title, String subtitle, Identifier icon, boolean success, float reveal) {
    }

    private static final Identifier WARNING_ICON =
            Identifier.fromNamespaceAndPath("skydiao", "textures/gui/dynamic_spot/alert_warning.png");
    private static final Identifier SUCCESS_ICON =
            Identifier.fromNamespaceAndPath("skydiao", "textures/gui/dynamic_spot/alert_success.png");

    private static final String AUTOPET_PREFIX = "§cAutopet §eequipped your ";
    private static final String LOW_TPS_TITLE = "Low TPS Alert";
    private static final double LOW_TPS_ENTER = 15.0;
    private static final double LOW_TPS_EXIT = 16.5;
    private static final long ENTER_MS = 220L;
    private static final long EXIT_MS = 200L;
    private static final int MAX_ALERTS = 4;

    private static final List<Alert> ALERTS = new ArrayList<>();

    @Override
    public String getListenerName() {
        return "AlertListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        ClientTickEvents.START_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ALERTS.clear());
    }

    private void onChat(Component component, boolean overlay) {
        String text = component.getString();
        if (!text.startsWith(AUTOPET_PREFIX)) return;
        int end = text.indexOf('!', AUTOPET_PREFIX.length());
        if (end <= AUTOPET_PREFIX.length()) return;
        String pet = ToolList.getInstance()
                .deleteColorCode(text.substring(AUTOPET_PREFIX.length(), Math.max(AUTOPET_PREFIX.length(), end - 2)))
                .trim();
        if (pet.isEmpty()) return;
        pushAutopet(new Alert(Kind.SUCCESS, "Autopet", "equipped " + pet, System.currentTimeMillis(), 5000L, 0L));
    }

    private static void pushAutopet(Alert alert) {
        ALERTS.removeIf(existing -> existing.kind == alert.kind && existing.subtitle.equals(alert.subtitle));
        ALERTS.add(0, alert);
        while (ALERTS.size() > MAX_ALERTS) {
            ALERTS.remove(ALERTS.size() - 1);
        }
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        double tps = AbstractListener.tpsListener.getCurrentTPS();
        boolean available = AbstractListener.tpsListener.isTPSAvaliable();
        boolean low = available && tps > 0.0 && tps < LOW_TPS_ENTER;
        boolean recovered = !available || tps >= LOW_TPS_EXIT;

        int index = indexOf(LOW_TPS_TITLE);
        if (low) {
            String subtitle = String.format(Locale.ROOT, "%.0f TPS · Server may be lagging", tps);
            if (index < 0) {
                ALERTS.add(0, new Alert(Kind.WARNING, LOW_TPS_TITLE, subtitle, now, 0L, 0L));
            } else {
                Alert current = ALERTS.get(index);
                if (!current.subtitle.equals(subtitle) || current.removedAt != 0L) {
                    ALERTS.set(index, new Alert(current.kind, current.title, subtitle, current.createdAt, 0L, 0L));
                }
            }
        } else if (index >= 0 && recovered) {
            Alert current = ALERTS.get(index);
            if (current.removedAt == 0L) {
                ALERTS.set(index, new Alert(current.kind, current.title, current.subtitle, current.createdAt, 0L, now));
            }
        }

        for (int i = 0; i < ALERTS.size(); i++) {
            Alert alert = ALERTS.get(i);
            if (alert.removedAt == 0L && alert.durationMs > 0L && now - alert.createdAt >= alert.durationMs - EXIT_MS) {
                ALERTS.set(i, new Alert(alert.kind, alert.title, alert.subtitle, alert.createdAt, alert.durationMs, now));
            }
        }
        ALERTS.removeIf(alert -> alert.removedAt > 0L && now - alert.removedAt >= EXIT_MS);
    }

    public static List<Row> rows() {
        long now = System.currentTimeMillis();
        List<Row> rows = new ArrayList<>(ALERTS.size());
        for (Alert alert : ALERTS) {
            float reveal = easeOutExpo((now - alert.createdAt) / (float) ENTER_MS);
            if (alert.removedAt > 0L) {
                reveal = Math.min(reveal, 1F - easeOutExpo((now - alert.removedAt) / (float) EXIT_MS));
            }
            reveal = Math.max(0F, Math.min(1F, reveal));
            boolean success = alert.kind == Kind.SUCCESS;
            rows.add(new Row(alert.title, alert.subtitle, success ? SUCCESS_ICON : WARNING_ICON, success, reveal));
        }
        return rows;
    }

    private static float easeOutExpo(float progress) {
        float clamped = Math.max(0F, Math.min(1F, progress));
        return clamped >= 1F ? 1F : 1F - (float) Math.pow(2, -10 * clamped);
    }

    private static int indexOf(String title) {
        for (int i = 0; i < ALERTS.size(); i++) {
            if (ALERTS.get(i).title.equals(title)) return i;
        }
        return -1;
    }
}
