package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class CustomSettingEntityESPListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "CustomSettingEntityESPListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        load();
    }

    private void onLastRender(LevelRenderContext context) {
        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity)) continue;

            for (Entry entry : entries) {
                if (entry.match(livingEntity)) {
                    RenderUtils.renderESP(wrLine, livingEntity, entry.color.getRed() / 255f, entry.color.getGreen() / 255f, entry.color.getBlue() / 255f, 1, false);
                }
            }
            for (Entry entry : tempEntries) {
                if (entry.match(livingEntity)) {
                    RenderUtils.renderESP(wrLine, livingEntity, entry.color.getRed() / 255f, entry.color.getGreen() / 255f, entry.color.getBlue() / 255f, 1, false);
                }
            }
        }

        wrLine.finishDraw();
    }

    public enum FilterType {
        ENTITY_TYPE,
        ARMORSTAND,
        ;
    }

    public record Entry(String entityType, FilterType filterType, Color color) {
        public JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("entityType", entityType);
            jsonObject.addProperty("filterType", filterType.name());
            jsonObject.addProperty("color", color.getRGB());
            return jsonObject;
        }

        public static Entry fromJsonObject(JsonObject jsonObject) {
            return new Entry(
                    jsonObject.get("entityType").getAsString(),
                    FilterType.valueOf(jsonObject.get("filterType").getAsString()),
                    new Color(jsonObject.get("color").getAsInt()));
        }

        public boolean match(LivingEntity livingEntity) {
            return switch (filterType) {
                case ENTITY_TYPE -> entityType.equals(EntityType.getKey(livingEntity.getType()).toString());
                default -> Optional.ofNullable(e2AMappingListener.getMobInfo(livingEntity)).map(m -> m.armorStand).map(armorStand -> ToolList.getInstance().deleteColorCode(armorStand.getName().getString()).toLowerCase().contains(entityType.toLowerCase())).orElse(false);
            };
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> tempEntries = new ArrayList<>();

    public Iterator<Entry> getEntries() {
        return entries.iterator();
    }

    public Iterator<Entry> getTempEntries() {
        return tempEntries.iterator();
    }

    public void add(String entityType, FilterType filterType, Color color) {
        remove(entityType);
        entries.add(new Entry(entityType, filterType, color));
        save();
    }

    public void addTemp(String entityType, FilterType filterType, Color color) {
        removeTemp(entityType);
        tempEntries.add(new Entry(entityType, filterType, color));
    }

    public void remove(String entityType) {
        entries.removeIf(e -> e.entityType.equals(entityType));
        save();
    }

    public void removeTemp(String entityType) {
        tempEntries.removeIf(e -> e.entityType.equals(entityType));
    }

    private void save() {
        JsonArray array = new JsonArray();
        entries.forEach(entry -> array.add(entry.toJsonObject()));
        ConfigManager.customSettingEntityESP.setValue(array.toString());
        ConfigManager.saveConfig();
    }

    private void load() {
        try {
            JsonArray array = JsonParser.parseString(ConfigManager.customSettingEntityESP.getValue()).getAsJsonArray().getAsJsonArray();
            entries.clear();
            array.forEach(entry -> entries.add(Entry.fromJsonObject(entry.getAsJsonObject())));
        } catch (Exception e) {}
    }

}
