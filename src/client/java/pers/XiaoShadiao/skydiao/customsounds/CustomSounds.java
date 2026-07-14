package pers.XiaoShadiao.skydiao.customsounds;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class CustomSounds {
    private CustomSounds() {
        // private empty constructor to avoid accidental instantiation
    }

    // ITEM_METAL_WHISTLE is the name of the custom sound event
    // and is called in the mod to use the custom sound
    public static final SoundEvent STAR_RAIL_NOTIFICATION = registerSound("star_rail_notification");
    public static final SoundEvent ALERT_MACRO_CHECK = registerSound("alert_macro_check");
    public static final SoundEvent YSCS = registerSound("yscs");
    public static final SoundEvent YSC = registerSound("ysc");
    public static final SoundEvent YSENTER = registerSound("ysenter");
    public static final SoundEvent YSWARNING = registerSound("yswarning");


    // actual registration of all the custom SoundEvents
    private static SoundEvent registerSound(String id) {
        Identifier identifier = Identifier.fromNamespaceAndPath("skydiao", id);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, identifier, SoundEvent.createVariableRangeEvent(identifier));
    }

    // This static method starts class initialization, which then initializes
    // the static class variables (e.g. ITEM_METAL_WHISTLE).
    public static void initialize() {
        // ExampleModSounds.LOGGER.info("Registering " + ExampleModSounds.MOD_ID + " Sounds");
        // Technically this method can stay empty, but some developers like to notify
        // the console, that certain parts of the mod have been successfully initialized
    }
}
