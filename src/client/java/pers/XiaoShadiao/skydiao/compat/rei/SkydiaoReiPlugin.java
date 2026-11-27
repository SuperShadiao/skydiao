package pers.XiaoShadiao.skydiao.compat.rei;

import dev.architectury.event.EventResult;
import me.shedaniel.rei.api.client.entry.renderer.EntryRendererRegistry;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.category.visibility.CategoryVisibilityPredicate;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.CollapsibleEntryRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SelectableRecipe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.neu.NeuIds;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Skyblock 物品列表和配方的 REI 适配入口。
 */
public class SkydiaoReiPlugin implements REIClientPlugin {

    private static final Logger LOGGER = LogManager.getLogger("XSD REI");
    private static final Object ENTRY_LOCK = new Object();
    private static boolean rebuildScheduled;
    private static boolean refreshStarted;
    private static boolean tickHookRegistered;
    private static String registeredVersion = "";
    private static String recipeSyncVersion;

    @Override
    public void registerCategories(CategoryRegistry registry) {
        LOGGER.info("开始注册 REI 分类");
        ensureRefresh();
        ensureTickHook();

        ItemStack craftingIcon = new ItemStack(Items.CRAFTING_TABLE);
        ItemStack forgeIcon = new ItemStack(Items.ANVIL);
        ItemStack npcShopIcon = new ItemStack(Items.GOLD_NUGGET);
        ItemStack katIcon = new ItemStack(Items.BONE);

        registry.add(new SkyblockRecipeCategory(SkyblockRecipeDisplay.CRAFTING_ID,
                Component.literal("Skyblock Crafting"), craftingIcon, 73));
        registry.add(new SkyblockRecipeCategory(SkyblockRecipeDisplay.FORGE_ID,
                Component.literal("Skyblock Forge"), forgeIcon, 84));
        registry.add(new SkyblockRecipeCategory(SkyblockRecipeDisplay.NPC_SHOP_ID,
                Component.literal("Skyblock NPC Shop"), npcShopIcon, 73));
        registry.add(new SkyblockRecipeCategory(SkyblockRecipeDisplay.KAT_ID,
                Component.literal("Kat Upgrade"), katIcon, 64));
        registry.add(new NpcTrackCategory());
        registry.add(new WikiCategory());
        registry.add(new MobDropCategory());

        registry.addWorkstations(CategoryIdentifier.of(NpcTrackDisplay.CATEGORY_ID),
                EntryStacks.of(new ItemStack(Items.COMPASS)));
        registry.addWorkstations(CategoryIdentifier.of(WikiDisplay.CATEGORY_ID),
                EntryStacks.of(new ItemStack(Items.BOOK)));
        registry.addWorkstations(CategoryIdentifier.of(SkyblockRecipeDisplay.CRAFTING_ID), EntryStacks.of(craftingIcon));
        registry.addWorkstations(CategoryIdentifier.of(SkyblockRecipeDisplay.FORGE_ID), EntryStacks.of(forgeIcon));
        registry.addWorkstations(CategoryIdentifier.of(SkyblockRecipeDisplay.NPC_SHOP_ID), EntryStacks.of(npcShopIcon));
        registry.addWorkstations(CategoryIdentifier.of(SkyblockRecipeDisplay.KAT_ID), EntryStacks.of(katIcon));

        // 只有 Crafting 和 NPC Location 支持“+”；其他分类由 TransferHandler 判定为不可用，按钮会显示为禁用。
        registry.registerVisibilityPredicate(new CategoryVisibilityPredicate() {
            @Override
            public double getPriority() {
                return 0;
            }

            @Override
            public EventResult handleCategory(DisplayCategory<?> category) {
                if (!ToolList.isInSkyblock()) {
                    return EventResult.pass();
                }
                Identifier identifier = category.getIdentifier();
                if (identifier != null
                        && "minecraft".equals(identifier.getNamespace())
                        && identifier.getPath().startsWith("plugins/")
                        && !identifier.getPath().equals("plugins/tag")) {
                    return EventResult.interruptFalse();
                }
                return EventResult.pass();
            }
        });
    }

    @Override
    public void registerEntryRenderers(EntryRendererRegistry registry) {
        registry.transformTooltip(VanillaEntryTypes.ITEM, (stack, point, tooltip) -> {
            if (stack.getValue() instanceof ItemStack itemStack) {
                String neuId = NeuIds.of(itemStack);
                if (!neuId.isEmpty()) {
                    tooltip.add(Component.literal("Skyblock ID: " + neuId).withStyle(ChatFormatting.DARK_AQUA));
                    removeMinecraftSourceLine(tooltip);
                    tooltip.add(Component.literal("Skyblock").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            return tooltip;
        });
    }

    private static void removeMinecraftSourceLine(me.shedaniel.rei.api.client.gui.widgets.Tooltip tooltip) {
        var entries = tooltip.entries();
        for (int i = entries.size() - 1; i >= Math.max(0, entries.size() - 4); i--) {
            try {
                if (entries.get(i).isText()
                        && "Minecraft".equalsIgnoreCase(entries.get(i).getAsText().getString())) {
                    entries.remove(i);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(new SkyblockTransferHandler());
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        if (registry.getGlobalDisplayGenerators().stream()
                .noneMatch(generator -> generator instanceof SkyblockRecipeDisplayGenerator)) {
            registry.registerGlobalDisplayGenerator(new SkyblockRecipeDisplayGenerator());
        }
        if (registry.getGlobalDisplayGenerators().stream()
                .noneMatch(generator -> generator instanceof NpcTrackDisplayGenerator)) {
            registry.registerGlobalDisplayGenerator(new NpcTrackDisplayGenerator());
        }
        if (registry.getGlobalDisplayGenerators().stream()
                .noneMatch(generator -> generator instanceof WikiDisplayGenerator)) {
            registry.registerGlobalDisplayGenerator(new WikiDisplayGenerator());
        }
        if (registry.getGlobalDisplayGenerators().stream()
                .noneMatch(generator -> generator instanceof MobDropDisplayGenerator)) {
            registry.registerGlobalDisplayGenerator(new MobDropDisplayGenerator());
        }
    }

    @Override
    public void registerEntries(EntryRegistry registry) {
        registeredVersion = "";
        LOGGER.info("REI registerEntries: skyblock={}, loaded={}",
                ToolList.isInSkyblock(), NeuItemRepository.INSTANCE.isLoaded());
        if (!ToolList.isInSkyblock()) {
            return;
        }

        ensureRefresh();
        NeuItemRepository repository = NeuItemRepository.INSTANCE;
        if (!repository.isLoaded()) {
            if (!rebuildScheduled) {
                rebuildScheduled = true;
                repository.runAfterLoad(() ->
                        Minecraft.getInstance().execute(SkydiaoReiPlugin::rebuildEntries));
            }
            return;
        }
        rebuildEntries(registry);
    }

    /**
     * 由客户端主初始化器调用。即使 REI 尚未发生首次 reload，也要先启动 NEU 数据加载
     * 并注册 tick 钩子；否则 REI 会因为 Hypixel 不发送配方包而一直停留在未初始化状态。
     */
    public static void bootstrap() {
        ensureRefresh();
        ensureTickHook();
    }

    private static void ensureRefresh() {
        if (refreshStarted) {
            return;
        }
        refreshStarted = true;
        NeuItemRepository.INSTANCE.refreshAsync(false).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn("NEU 数据刷新失败，将在需要时重试", throwable);
                refreshStarted = false;
            }
        });
    }

    private static void ensureTickHook() {
        if (tickHookRegistered) {
            return;
        }
        tickHookRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!ToolList.isInSkyblock()) {
                return;
            }
            NeuItemRepository repository = NeuItemRepository.INSTANCE;
            ensureRecipeSync(repository.version());
            if (!repository.isLoaded() || repository.version().equals(registeredVersion)) {
                return;
            }
            rebuildEntries();
        });
    }

    /**
     * Hypixel 不发送原版配方，REI 可能因此不会完成首次 reload。
     * 手动发送一个空的配方同步包，强制 REI 重建 entry / display 注册表。
     */
    private static void ensureRecipeSync(String version) {
        if (version.equals(recipeSyncVersion)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            return;
        }
        try {
            recipeSyncVersion = version;
            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(
                    Map.of(), SelectableRecipe.SingleInputSet.empty());
            client.getConnection().handleUpdateRecipes(packet);
            LOGGER.info("已发送空配方同步包以触发 REI reload");
        } catch (Throwable throwable) {
            recipeSyncVersion = "";
            LOGGER.warn("触发 REI 配方同步失败", throwable);
        }
    }

    private static void rebuildEntries() {
        rebuildEntries(EntryRegistry.getInstance());
    }

    private static void rebuildEntries(EntryRegistry registry) {
        if (registry == null || !ToolList.isInSkyblock() || !NeuItemRepository.INSTANCE.isLoaded()) {
            return;
        }

        synchronized (ENTRY_LOCK) {
            registeredVersion = NeuItemRepository.INSTANCE.version();
            try {
                List<EntryStack<ItemStack>> stacks = new ArrayList<>(NeuItemRepository.INSTANCE.itemCount());
                int failed = 0;
                int barriers = 0;
                for (NeuItemRepository.NeuItem item : NeuItemRepository.INSTANCE.items()) {
                    try {
                        ItemStack stack = NeuItemRepository.INSTANCE.createDisplayStack(item);
                        if (stack.isEmpty()) {
                            continue;
                        }
                        if (stack.is(Items.BARRIER)) {
                            barriers++;
                            continue;
                        }
                        stacks.add(EntryStacks.of(stack));
                    } catch (Throwable throwable) {
                        failed++;
                        if (failed <= 5) {
                            LOGGER.warn("创建 REI 物品失败: {}", item.internalName(), throwable);
                        }
                    }
                }

                registry.removeEntryIf(_ -> true);
                registry.addEntries(stacks);
                registry.refilter();
                registerGroups(CollapsibleEntryRegistry.getInstance());
                registeredVersion = NeuItemRepository.INSTANCE.version();
                LOGGER.info("REI 物品列表已替换为 {} 个 Skyblock 物品（跳过屏障 {}，异常 {}）", stacks.size(), barriers, failed);
            } catch (Throwable throwable) {
                LOGGER.error("替换 REI 物品列表失败", throwable);
            } finally {
                rebuildScheduled = false;
            }
        }
    }

    @Override
    public void registerCollapsibleEntries(CollapsibleEntryRegistry registry) {
        registerGroups(registry);
    }

    private static void registerGroups(CollapsibleEntryRegistry registry) {
        if (registry == null) {
            return;
        }
        NeuItemRepository repository = NeuItemRepository.INSTANCE;
        if (!repository.isLoaded() || repository.parents().isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<String>> entry : repository.parents().entrySet()) {
            ItemStack parent = repository.find(entry.getKey())
                    .map(repository::createDisplayStack)
                    .orElse(ItemStack.EMPTY);
            if (parent.isEmpty()) {
                continue;
            }

            List<EntryStack<ItemStack>> group = new ArrayList<>(entry.getValue().size() + 1);
            group.add(EntryStacks.of(parent));
            for (String childId : entry.getValue()) {
                ItemStack child = repository.find(childId)
                        .map(repository::createDisplayStack)
                        .orElse(ItemStack.EMPTY);
                if (!child.isEmpty()) {
                    group.add(EntryStacks.of(child));
                }
            }
            if (group.size() <= 1) {
                continue;
            }

            registry.group(
                    Identifier.fromNamespaceAndPath("skydiao", "rei_group/" + safePath(entry.getKey())),
                    parent.getHoverName(),
                    group
            );
        }
    }

    private static String safePath(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace(';', '_')
                .replace(':', '_')
                .replace(' ', '_');
    }

    @Override
    public double getPriority() {
        return -50;
    }
}
