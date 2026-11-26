package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * NEU 配方在 REI 中的展示数据。
 */
public class SkyblockRecipeDisplay implements Display {

    public static final Identifier CRAFTING_ID = id("skyblock_crafting");
    public static final Identifier FORGE_ID = id("skyblock_forge");
    public static final Identifier NPC_SHOP_ID = id("skyblock_npc_shop");
    public static final Identifier KAT_ID = id("skyblock_kat_upgrade");

    private final NeuItemRepository.Recipe recipe;

    public SkyblockRecipeDisplay(NeuItemRepository.Recipe recipe) {
        this.recipe = recipe;
    }

    public NeuItemRepository.Recipe recipe() {
        return recipe;
    }

    public boolean isSupported() {
        return switch (recipe.type()) {
            case "crafting", "forge", "npc_shop", "katgrade" -> true;
            default -> false;
        };
    }

    public Identifier categoryId() {
        return switch (recipe.type()) {
            case "crafting" -> CRAFTING_ID;
            case "forge" -> FORGE_ID;
            case "npc_shop" -> NPC_SHOP_ID;
            case "katgrade" -> KAT_ID;
            default -> CRAFTING_ID;
        };
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        List<EntryIngredient> entries = new ArrayList<>();
        for (String token : recipe.inputTokens()) {
            ItemStack stack = RecipeStacks.resolve(token);
            if (!stack.isEmpty()) {
                entries.add(EntryIngredient.of(EntryStacks.of(stack)));
            }
        }
        return entries;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        List<EntryIngredient> entries = new ArrayList<>();
        for (String token : recipe.outputTokens()) {
            ItemStack stack = RecipeStacks.resolveOutput(token, recipe.count());
            if (!stack.isEmpty()) {
                entries.add(EntryIngredient.of(EntryStacks.of(stack)));
            }
        }
        return entries;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return CategoryIdentifier.of(categoryId());
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.empty();
    }

    @Override
    public DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }

    public String extraText() {
        return recipe.extraText();
    }

    public List<SlotSpec> slots(int width, int height) {
        return switch (recipe.type()) {
            case "crafting" -> craftingSlots(width, height);
            case "forge" -> forgeSlots(width, height);
            case "npc_shop" -> npcShopSlots(width, height);
            case "katgrade" -> katSlots(width, height);
            default -> List.of();
        };
    }

    private List<SlotSpec> craftingSlots(int width, int height) {
        boolean hasText = recipe.extraText() != null && !recipe.extraText().isBlank();
        int startX = width / 2 - 58;
        int startY = height / 2 - (hasText ? 32 : 27);

        List<SlotSpec> slots = new ArrayList<>(10);
        List<String> keys = List.of("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3");
        for (int i = 0; i < keys.size(); i++) {
            ItemStack stack = RecipeStacks.resolve(recipe.grid().get(keys.get(i)));
            if (!stack.isEmpty()) {
                int x = startX + 1 + (i % 3) * 18;
                int y = startY + 1 + (i / 3) * 18;
                slots.add(new SlotSpec(x, y, stack, false));
            }
        }
        if (recipe.output() != null) {
            slots.add(new SlotSpec(startX + 95, startY + 19, RecipeStacks.resolveOutput(recipe.output(), recipe.count()), true));
        }
        return slots;
    }

    private List<SlotSpec> forgeSlots(int width, int height) {
        List<String> inputs = recipe.inputs();
        int count = inputs.size();
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
        int rows = Math.max(1, (int) Math.ceil(count / (double) columns));

        int startX = width / 2 - (columns * 18) / 2 - 14;
        int startY = height / 2 - (rows * 18) / 2;

        List<SlotSpec> slots = new ArrayList<>(count + 1);
        for (int i = 0; i < count; i++) {
            ItemStack stack = RecipeStacks.resolve(inputs.get(i));
            if (!stack.isEmpty()) {
                slots.add(new SlotSpec(startX + (i % columns) * 18, startY + (i / columns) * 18, stack, false));
            }
        }
        ItemStack output = RecipeStacks.resolveOutput(recipe.output(), recipe.count());
        if (!output.isEmpty()) {
            slots.add(new SlotSpec(width / 2 + 46, height / 2 - 9, output, true));
        }
        return slots;
    }

    private List<SlotSpec> npcShopSlots(int width, int height) {
        List<SlotSpec> slots = new ArrayList<>();
        int y = height / 2 - 9;

        ItemStack npc = RecipeStacks.resolve(recipe.npc());
        if (npc.isEmpty()) {
            npc = RecipeStacks.resolve(recipe.sourceItem());
        }
        if (!npc.isEmpty()) {
            slots.add(new SlotSpec(10, y, npc, false));
        }

        int costX = 34;
        for (String token : recipe.cost()) {
            ItemStack stack = RecipeStacks.resolve(token);
            if (!stack.isEmpty()) {
                slots.add(new SlotSpec(costX, y, stack, false));
                costX += 18;
            }
        }

        ItemStack output = RecipeStacks.resolveOutput(recipe.output(), recipe.count());
        if (!output.isEmpty()) {
            slots.add(new SlotSpec(width - 28, y, output, true));
        }
        return slots;
    }

    private List<SlotSpec> katSlots(int width, int height) {
        List<SlotSpec> slots = new ArrayList<>();
        int y = height / 2 - 9;

        ItemStack input = RecipeStacks.resolve(recipe.input());
        if (!input.isEmpty()) {
            slots.add(new SlotSpec(10, y, input, false));
        }

        int itemX = 30;
        for (String token : recipe.items()) {
            ItemStack stack = RecipeStacks.resolve(token);
            if (!stack.isEmpty()) {
                slots.add(new SlotSpec(itemX, y, stack, false));
                itemX += 18;
            }
        }
        if (recipe.coins() > 0) {
            slots.add(new SlotSpec(itemX, y, RecipeStacks.resolve("SKYBLOCK_COIN:" + recipe.coins()), false));
        }

        ItemStack output = RecipeStacks.resolveOutput(recipe.output(), recipe.count());
        if (!output.isEmpty()) {
            slots.add(new SlotSpec(width - 28, y, output, true));
        }
        return slots;
    }

    public int arrowX(int width) {
        return switch (recipe.type()) {
            case "crafting" -> width / 2 - 58 + 60;
            case "forge", "katgrade", "npc_shop" -> width / 2 + 20;
            default -> width / 2;
        };
    }

    public int arrowY(int height) {
        return switch (recipe.type()) {
            case "crafting" -> {
                boolean hasText = recipe.extraText() != null && !recipe.extraText().isBlank();
                yield height / 2 - (hasText ? 32 : 27) + 18;
            }
            default -> height / 2 - 9;
        };
    }

    public String durationText() {
        if (recipe.type().equals("forge") && recipe.duration() > 0) {
            return formatDuration(recipe.duration());
        }
        if (recipe.type().equals("katgrade") && recipe.time() > 0) {
            return formatDuration(recipe.time());
        }
        return "";
    }

    private static String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("skydiao", path.toLowerCase(Locale.ROOT));
    }

    public record SlotSpec(int x, int y, ItemStack stack, boolean output) {
    }
}
