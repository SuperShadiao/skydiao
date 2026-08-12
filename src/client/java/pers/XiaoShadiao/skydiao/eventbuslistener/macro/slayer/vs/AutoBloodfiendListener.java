package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs;


import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.IMacro;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task.*;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.pathfinder.PathFinder;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class AutoBloodfiendListener extends AbstractListener implements IMacro {

    private boolean thisBossDisabled;

    @Override
    public String getListenerName() {
        return "AutoBloodfiendListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onWorldUnload);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        CustomFabricEvents.ON_SIMULATOR_CLICK.register(this::onSimulatorClick);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseClick);
    }

    private LivingEntity currentAttackedEntity;

    private boolean onMouseClick(long hwid, MouseButtonInfo mouseButtonInfo, int state) {
        if(mouseButtonInfo.button() == 0 && state == 1 && mc.screen == null) {
            if(mc.hitResult instanceof EntityHitResult entityHitResult) {
                currentAttackedEntity = entityHitResult.getEntity().asLivingEntity();
            }
        }
        return false;
    }

    private int tickCount = 0;

    public final List<BFTask> tasks = new ArrayList<>();
    private final Map<BFTask, Integer> endedTasks = new HashMap<>();

    private BFTask currentTask;

    private E2AMappingListener.MobInfo bloodfiendInstance;
    private LivingEntity bloodfiendEntityInstance;

    private BlockPos clotgoyleDeadPos = BlockPos.ZERO;
    private Set<BlockPos> clotgoyleDeadPosList = new HashSet<>();

    public String debugSkills = null;
    private List<LivingEntity> clotgoyleList = new ArrayList<>();

    private int twinclawsCD = 0;
    private boolean killerSpringFlag = false;
    private int healingTickCD = 0;
    private String impleTitle = "";
    private int impleCD = 0;
    public ArmorStand bloodIchorEntity;
    public int bloodIchorDuringTick;
    public E2AMappingListener.MobInfo getBloodfiendInstance() {
        return bloodfiendInstance;
    }
    public LivingEntity getBloodfiendEntityInstance() {
        return bloodfiendEntityInstance;
    }
    public BlockPos getClotgoyleDeadPos() {
        return clotgoyleDeadPos;
    }
    public void setDebugSkills(String s) {
        debugSkills = s;
    }
    public void killTask() {
        BFTask ele = tasks.remove(0);
        if(ele instanceof BFTaskStonewrath) clotgoyleDeadPosList.clear();
    }
    public ArmorStand getBloodIchorEntity() {
        return bloodIchorEntity;
    }
    public BlockPos getBloodIchorPos() {
        return bloodIchorEntity == null ?  BlockPos.ZERO : BlockPos.containing(bloodIchorEntity.getX(), bloodIchorEntity.getY(), bloodIchorEntity.getZ());
    }
    public List<LivingEntity> getClotgoyleList() {
        return clotgoyleList;
    }
    public long startTime = System.currentTimeMillis();
    public int dmgBoostCDTick = 0;
    public <T extends BFTask> T getRunningTaskByClass(Class<T> clazz) {
        for (BFTask task : tasks) {
            if(clazz.isInstance(task)) return clazz.cast(task);
        }
        return null;
    }
    public int getTwinclawsCD() {
        return twinclawsCD;
    }

    public List<BlockPos> maniaIgnoreChangePos = new ArrayList<>();

    public void onClientTick(Minecraft mc) {

        if(checkIsDisabled()) return;
        tickCount++;

        if(bloodIchorEntity != null) {
            bloodIchorDuringTick++;
            if(!ToolList.getInstance().isEntityOnWorld(bloodIchorEntity)) bloodIchorEntity = null;
        } else bloodIchorDuringTick = 0;

        if(healingTickCD > 0) healingTickCD--;
        if(impleCD > 0 && !hasTask(BFTaskImpelHandler.class)) {
            impleCD--;
            if(impleCD == 0) impleTitle = "";
        }

        E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(currentAttackedEntity);
        if(bloodfiendEntityInstance == null && mobInfo != null && mobInfo.armorStand != null && mobInfo.armorStand.getName().getString().contains("Bloodfiend")) {
            thisBossDisabled = false;
            bloodfiendInstance = mobInfo;
            bloodfiendEntityInstance = currentAttackedEntity;
            if(!hasTask(BFTaskAttacker.class)) {
                tasks.addFirst(new BFTaskAttacker());
            }
            if(!hasTask(BFTaskClotgoyleAttacker.class)) {
                tasks.addFirst(new BFTaskClotgoyleAttacker());
            }
            clotgoyleDeadPosList.clear();
            PathFinder.registerConfig(new SlayerPFConfig());
            startTime = System.currentTimeMillis();
            dmgBoostCDTick = 600;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e使用/skydiaostopabf来停止本次自动Slayer!"));
        }
        if(bloodfiendEntityInstance != null) {
            E2AMappingListener.MobInfo mobInfo2 = e2AMappingListener.getMobInfo(bloodfiendEntityInstance);
            if(mobInfo2 != null) {
                bloodfiendInstance = mobInfo2;
            }
        }

        if(!ToolList.getInstance().isEntityOnWorld(bloodfiendEntityInstance)) {
            bloodfiendEntityInstance = null;
        }
        if(bloodfiendEntityInstance == null && debugSkills == null && (bloodfiendInstance != null || !tasks.isEmpty())) {
            System.out.println("Unloading...");
            thisBossDisabled = false;
            bloodfiendInstance = null;
            tasks.clear();
            clotgoyleDeadPosList.clear();
            PathFinder.ICustomPathfinderConfig config = PathFinder.getRegisteredConfig();
            if(config instanceof SlayerPFConfig instance) instance.done = true;
            MacroManagerListener.pathFinderExecutor.stopExecution();
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e本次用时" + (System.currentTimeMillis() - startTime) / 1000f + "s"));
        }

        if(thisBossDisabled) return;
//
//        if(debugSkills != null) {
//
//            if(!hasTask(BFTaskAttacker.class)) {
//                tasks.add(0, new BFTaskAttacker());
//            }
//            if(!hasTask(BFTaskClotgoyleAttacker.class)) {
//                tasks.add(0, new BFTaskClotgoyleAttacker());
//            }
//
//            if(bloodfiendEntityInstance == null) {
//                ToolList.getInstance().取玩家自身半径内实体(30).stream().filter(e -> e.getName().contains("Bloodfiend")).findAny().ifPresent(LivingEntity -> bloodfiendEntityInstance = LivingEntity);
//            }
//            if(bloodIchorEntity == null) {
//                ToolList.getInstance().取玩家自身半径内实体(15, ArmorStand.class).stream()
//                        .filter(e -> ToolList.getInstance().deleteColorCode(e.getName()).matches("\\d{1,2}\\.\\ds"))
//                        .filter(e -> Math.abs(e.posY - mc.thePlayer.posY) < 6.01)
//                        .findAny().ifPresent(ArmorStand -> {
//                            if(!hasTask(BFTaskManiaHandler.class)) tasks.add(0, new BFTaskBloodIchorHandler(true));
//                            bloodIchorEntity = ArmorStand;
//                        });
//            }
//
//            String skillInfo = debugSkills;
//            if(twinclawsCD <= 0 && skillInfo.contains("twinclaws 0.")) {
//                twinclawsCD = 8;
//                tasks.add(0, new BFTaskTwinclawsHandler());
//            } else if(twinclawsCD > 0 && !skillInfo.contains("twinclaws 0.")) {
//                twinclawsCD--;
//            }
//
//            if(bloodIchorEntity != null && skillInfo.contains("twinclaws") && !hasTask(BFTaskBloodIchorHandler.class) && !hasTask(BFTaskManiaHandler.class)) {
//                tasks.add(0, new BFTaskBloodIchorHandler(false));
//            }
//
//            if(skillInfo.contains("mania") && !hasTask(BFTaskManiaHandler.class)) {
//                tasks.add(0, new BFTaskManiaHandler());
//            }
//
//            if(skillInfo.contains("stonewrath 0.") && !hasTask(BFTaskStonewrath.class)) {
//                tasks.add(0, new BFTaskStonewrath());
//            }
//
//            if(healingTickCD <= 0 && mc.thePlayer.getHealth() < /*4.8*/Config管理.getConfigKeyValue("skyblockautobloodfiendlowhealth") - 0.2 + getExtraHealingHealth()) {
//                healingTickCD = 13;
//                tasks.add(0, new BFTaskHealHandler());
//            }
//
//            if(impleCD <= 0 && impleTitle.contains("Impel")) {
//                impleCD = 13;
//                tasks.add(0, new BFTaskImpelHandler(BFTaskImpelHandler.getType(impleTitle)));
//            }
//
//            if(skillInfo.contains("add")) {
//                debugSkills = "a";
//                tasks.add(0, new BFTaskClotgoyleAttacker());
//                tasks.add(0, new BFTaskAttacker());
//            }
//
//            if(!killerSpringFlag && skillInfo.contains("killer spring") && !hasTask(BFTaskKillerSpringHandler.class)) {
//                killerSpringFlag = true;
//                tasks.add(0, new BFTaskKillerSpringHandler());
//            } else if(killerSpringFlag && !skillInfo.contains("killer spring")) {
//                killerSpringFlag = false;
//            }
//            // debugSkills = null;
//        }

        if(bloodfiendInstance != null) {
            if(dmgBoostCDTick < 620) dmgBoostCDTick++;
            if(bloodfiendInstance.armorStandForBoss != null) {
                String skillInfo = ToolList.getInstance().deleteColorCode(bloodfiendInstance.armorStandForBoss.getName().getString().toLowerCase());

                if(twinclawsCD <= 0 && skillInfo.contains("twinclaws 0.")) {
                    twinclawsCD = 8;
                    tasks.addFirst(new BFTaskTwinclawsHandler());
                } else if(twinclawsCD > 0 && !skillInfo.contains("twinclaws 0.")) {
                    twinclawsCD--;
                }

                if(bloodIchorEntity == null) {
                    mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(15)).stream()
                            .filter(e -> ToolList.getInstance().deleteColorCode(e.getName().getString()).matches("\\d{1,2}\\.\\ds"))
                            .filter(e -> Math.abs(e.getY() - mc.player.getY()) < 6.01)
                            .findAny().ifPresent(ArmorStand -> {
                                bloodIchorEntity = ArmorStand;
                                if(!hasTask(BFTaskManiaHandler.class)) tasks.addFirst(new BFTaskBloodIchorHandler(true));
                            });
                }

                if(bloodIchorEntity != null && skillInfo.contains("twinclaws") && !hasTask(BFTaskBloodIchorHandler.class) && !hasTask(BFTaskManiaHandler.class)) {
                    tasks.addFirst(new BFTaskBloodIchorHandler(false));
                }

                if(skillInfo.contains("mania") && !hasTask(BFTaskManiaHandler.class)) {
                    tasks.addFirst(new BFTaskManiaHandler());
                }

                if(skillInfo.contains("stonewrath 0.") && !hasTask(BFTaskStonewrath.class)) {
                    tasks.addFirst(new BFTaskStonewrath());
                }

                if(healingTickCD <= 0 && mc.player.getHealth() < ConfigManager.skyblockautobloodfiendlowhealth.getValue() - 0.2 + getExtraHealingHealth()) {
                    healingTickCD = 13;
                    tasks.addFirst(new BFTaskHealHandler());
                }

                if(impleCD <= 0 && impleTitle.contains("Impel")) {
                    impleCD = 8;
                    tasks.addFirst(new BFTaskImpelHandler(BFTaskImpelHandler.getType(impleTitle)));
                }

                if(!killerSpringFlag && skillInfo.contains("killer spring") && !hasTask(BFTaskKillerSpringHandler.class) && !hasTask(BFTaskManiaHandler.class)) {
                    killerSpringFlag = true;
                    tasks.addFirst(new BFTaskKillerSpringHandler());
                } else if(killerSpringFlag && !skillInfo.contains("killer spring")) {
                    killerSpringFlag = false;
                }

                if(dmgBoostCDTick >= 620) {
                    if(!hasTask(BFTaskManiaHandler.class) && !hasTask(BFTaskStonewrath.class)) {
                        dmgBoostCDTick = 0;
                        tasks.addFirst(new BFTaskDmgBooster());
                    }
                }

            }
        }

        List<RemotePlayer> list = mc.level.getEntitiesOfClass(RemotePlayer.class, mc.player.getBoundingBox().inflate(hasTask(BFTaskManiaHandler.class) ? 1.4 : 8));
        List<LivingEntity> list2 = list.stream().filter(e -> e.getName().getString().contains("lotgoyle")/* && e.getHealth() > 0*/).collect(Collectors.toList());
        list2.removeIf(e -> {
            if(e.getHealth() <= 0) {
                clotgoyleDeadPosList.add(e.blockPosition());
                return true;
            }
            return false;
        });
        clotgoyleList = list2;
        clotgoyleDeadPos = clotgoyleDeadPosList.isEmpty() || tickCount % 20 != 1 ? clotgoyleDeadPos : Tools.findMinEnclosingCircle(clotgoyleDeadPosList);

        if (bloodfiendInstance != null || debugSkills != null || hasTask(BFTaskAttacker.class)) {
            if(!tasks.isEmpty()) {
                tasks.sort(Comparator.comparingInt(BFTask::priority).reversed());
                for (BFTask task : tasks) {
                    task.sleepAlsoTickUpdate();
                }
                BFTask t = tasks.getFirst();
                if(t != currentTask) {
                    if(currentTask != null) {
                        currentTask.onTaskResume();
                    }
                    t.onTaskStart(currentTask);
                    currentTask = t;
                }
                currentTask.tickUpdate();
                if(currentTask.isTaskFinished()) {
                    currentTask.onFinished();
                    if(currentTask instanceof BFTaskStonewrath) clotgoyleDeadPosList.clear();
                    tasks.remove(currentTask);
                    endedTasks.put(currentTask, 80);
                }
                for (Map.Entry<BFTask, Integer> entry : endedTasks.entrySet()) {
                    entry.setValue(entry.getValue() - 1);
                }
                endedTasks.entrySet().removeIf(entry -> entry.getValue() <= 0);
            }
        }

    }

    private static boolean checkIsDisabled() {
        return mc.player == null || mc.level == null || !ConfigManager.skyblockautobloodfiend.getValue() || !"rift".equals(StatusManager.get().getMode());
    }

    public void startSlayer() {
        tasks.addFirst(new BFTaskAttacker());
    }

    private boolean hasTask(Class<? extends BFTask> clazz) {
        if(Modifier.isAbstract(clazz.getModifiers())) return false;
        return tasks.stream().anyMatch(clazz::isInstance);
    }

    public void onChat(Component component, boolean b) {
        if(checkIsDisabled()) return;
        String message = ToolList.getInstance().deleteColorCode(component.getString());
        if(message.equals("JAILED! You lost all your ❤! You were sent to the Oubliette!")) {
            bloodfiendEntityInstance = null;

            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c========================================="));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c啊哦, 讨伐好像失败了...是小沙雕做的不够好吗?"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c再试一次吧!"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c提示:"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c1. 请在平坦的地方生成boss"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c2. 请远离其他玩家的boss处生成boss"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c3. 你的物品栏中需要一些关键物品:"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c - §cHealing Melon §a(T1需要, 用于治疗)"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c - §bHoly Ice §a(T2需要, 用于Twinclaws减伤)"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c - §fSteak Stake §a(T3需要, 后期方便直接秒杀节省时间)"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c4. 你需要一些关键的盔甲:"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c - §cBase Anti-Bite §a(T3需要, 用于减伤)"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c - §cUpgraded Anti-Bite §a(T4需要, 用于减伤)"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c5. 如果你需要打T5, 必须要有盔甲§fSepulture Chestplate§c,"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c   否则Mana(法力值)不够用于Mania的生存哦!"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c========================================="));
        }
        if(!tasks.isEmpty()) {
            for (BFTask task : tasks) {
                task.onChat(component);
            }
        }
    }

    private void onLastRender(LevelRenderContext context) {
        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_FILL);
        if (debugSkills != null) {
            BlockPos bp = clotgoyleDeadPos;
            RenderUtils.renderTrace(wrLine, bp, 0, 1, 1, 1);
            RenderUtils.renderESP(wrLine, bp, 0, 1, 1, 1, false);
            RenderUtils.renderESP(wrFill, bp, 0, 1, 1, 1, true);

            for (BlockPos bp2 : clotgoyleDeadPosList) {
                RenderUtils.renderESP(wrLine, bp2, 0, 1, 0.5f, 1, false);
            }
        }

        if (bloodfiendInstance != null) {
            BlockPos bp = clotgoyleDeadPos;
            RenderUtils.renderTrace(wrLine, bp, 0, 1, 1, 1);
            RenderUtils.renderESP(wrLine, bp, 0, 1, 1, 1, false);
            RenderUtils.renderESP(wrFill, bp, 0, 1, 1, 1, true);

            for (BlockPos bp2 : clotgoyleDeadPosList) {
                RenderUtils.renderESP(wrLine, bp2, 0, 1, 0.5f, 255f, false);
            }

            if (bloodfiendEntityInstance != null) {
                RenderUtils.renderESP(wrLine, bloodfiendEntityInstance, 1, 0, 0, 1, false);
            }
        }
        if(bloodfiendEntityInstance != null || bloodfiendInstance != null) {
            if (!tasks.isEmpty()) {
                for (BFTask task : tasks) {
                    task.render(wrLine, wrFill);
                }
            }
        }

        if(bloodIchorEntity != null) {
            BlockPos bp = getBloodIchorPos();
            RenderUtils.renderTrace(wrLine, bp, 1, 0, 0, 1);
            RenderUtils.renderESP(wrLine, bp, 1, 0, 0, 1, false);
            RenderUtils.renderESP(wrFill, bp, 1, 0, 0, 1, true);
        }

        wrFill.finishDraw();
        wrLine.finishDraw();
    }

    public class TaskRender extends XSDHUD {

        @Override
        public void runRegister() {
            HudElementRegistry.addLast(Objects.requireNonNull(Identifier.tryBuild("skydiao", "abf_task_render")), this);
        }

        @Override
        public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
            if(!force && checkIsDisabled()) return;
            if(force || bloodfiendInstance != null || debugSkills != null) {
                int i = context.guiHeight() / 2;
                int x = context.guiWidth() / 3;

                List<String> list = new ArrayList<>();
                list.add("§eABF Tasks:");
                for (BFTask t : tasks) {
                    list.add("§e" + t.getTaskName());
                }
                for (Map.Entry<BFTask, Integer> entry : endedTasks.entrySet()) {
                    list.add("§7" + entry.getKey().getTaskName() + " * Ended " + entry.getValue());
                }
                list.add("");
                list.add("Clotgoyle: x" + getClotgoyleList().size() + " " + clotgoyleDeadPos.toString());

                if (bloodIchorEntity != null) {
                    list.add("§cIchor: §b" + bloodIchorEntity.blockPosition() + " §cDuring: " + (int)(bloodIchorDuringTick / 20f) + "s Pre-Heal: " + getExtraHealingHealth());
                }
                list.add("§eUse §a/skydiaostopabf §eto stop!");

                for (String s : list) {
                    context.text(mc.font, s, x, i, 0xFFFFFFFF);
                    i += mc.font.lineHeight;
                }
            }
        }

        @Override
        public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter) {

        }

        @Override
        public String getHudName() {
            return "auto_bloodfiend_task_render";
        }

    }

    private void onWorldUnload(Minecraft mc, ClientLevel level) {
        debugSkills = null;
        bloodfiendInstance = null;
        bloodfiendEntityInstance = null;
        bloodIchorEntity = null;
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(checkIsDisabled()) return false;
        Component title = ToolList.getInstance().tryGetTitleFromPacket(packet);
        if (title != null) {
            impleTitle = title.getString();
        } else if (packet instanceof ClientboundPlayerPositionPacket velocity) {
            ToolList.TPInfo tpInfo = ToolList.getInstance().parseTPPacket(velocity);
            Vec3 velocityVec = tpInfo.to().deltaMovement();
            BFTaskManiaHandler task = getRunningTaskByClass(BFTaskManiaHandler.class);
            ToolList.printChatMessage(Component.literal("§a[小沙雕] X:" + velocityVec.x + " Y:" + velocityVec.y + " Z:" + velocityVec.z));
            if (task != null && (((Math.abs(velocityVec.x) > 0.5 && Math.abs(velocityVec.z) > 0.5)) || Math.abs(velocityVec.x) > 1 || Math.abs(velocityVec.z) > 1)) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §eKB!"));
                task.flagKB();
            }
        } else if(packet instanceof ClientboundBlockUpdatePacket blockPacket) {
            if(hasTask(BFTaskManiaHandler.class)) {
                if(blockPacket.getBlockState().getBlock() == Blocks.REDSTONE_BLOCK || blockPacket.getBlockState().getBlock().getDescriptionId().endsWith("terracotta") || blockPacket.getBlockState().getBlock() == Blocks.COAL_BLOCK) {

                } else {
                    BlockPos pos = blockPacket.getPos();
                    if(maniaIgnoreChangePos.contains(pos)) {
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e忽略了坐标" + pos + "的改变!"));
                        maniaIgnoreChangePos.remove(pos);
                        return true;
                    }
                }
            } else {
                maniaIgnoreChangePos.clear();
            }
        }
        return false;
    }

    private boolean onSimulatorClick(CustomFabricEvents.SimulatorClickType simulatorClickType) {
        if(simulatorClickType == CustomFabricEvents.SimulatorClickType.LEFT) {
            if(mc.hitResult instanceof EntityHitResult result && result.getEntity() == bloodfiendEntityInstance) {
                if(currentTask instanceof BFTaskClotgoyleAttacker) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c已防止你在攻击Clotgoyle时攻击Bloodfiend!"));
                    return true;
                }
                if(currentTask instanceof BFTaskKillerSpringHandler) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c已防止你在攻击血柱时攻击Bloodfiend!"));
                    return true;
                }
            }
        }
        return false;
    }

    private int getExtraHealingHealth() {

        float f = bloodIchorDuringTick / 20f;

        int actuall;
        try {
            actuall = ((Callable<Integer>) () -> {
                if (f < 3) {
                    return 0;
                } else if (f < 5) {
                    return 1;
                } else if (f < 7) {
                    return 2;
                } else return 4;
            }).call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        BFTaskStonewrath task = getRunningTaskByClass(BFTaskStonewrath.class);
        if (task != null && task.getEscapeState()) actuall = 10 - ConfigManager.skyblockautobloodfiendlowhealth.getValue() / 2 + (actuall / 2 + 1);

        return actuall;
    }

    public void disableThisBoss() {
        thisBossDisabled = true;
        MacroManagerListener.pathFinderExecutor.stopExecution();
    }

    @Override
    public boolean isMacroActive() {
        return false;
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        return false;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        return false;
    }

    @Override
    public String getMacroName() {
        return "";
    }

    public class SlayerPFConfig implements PathFinder.ICustomPathfinderConfig {
        boolean done = false;
        @Override
        public boolean isAllowBreak() {
            return false;
        }

        @Override
        public boolean isAllowPlace() {
            return false;
        }

        @Override
        public boolean shouldUnregister() {
            return done;
        }

        @Override
        public int getDepth() {
            return 10000;
        }

        @Override
        public long getTimeout() {
            return currentTask instanceof BFTaskKillerSpringHandler ? 50 : 200;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        public boolean shouldStopWhenRecieveS08() {
            return false;
        }
    }

    public static class Tools {
        /**
         * 找到包含所有 BlockPos 的最小覆盖圆的圆心和半径
         * @param positions 坐标列表
         * @return 圆心（BlockPos）和半径（double）
         */
        public static BlockPos findMinEnclosingCircle(Collection<BlockPos> positions) {
            if (positions == null || positions.isEmpty()) {
                throw new IllegalArgumentException("坐标列表不能为空");
            }

            // 随机打乱列表以优化算法性能
            List<BlockPos> shuffled = new ArrayList<>(positions);
            // Collections.shuffle(shuffled, ToolList.getInstance().random);

            // 初始化圆：第一个点作为圆心，半径为0
            BlockPos center = shuffled.get(0);
            double radius = 0;

            // 逐步扩展圆
            for (int i = 1; i < shuffled.size(); i++) {
                BlockPos point = shuffled.get(i);
                if (!isInsideCircle(point, center, radius)) {
                    // 当前点不在圆内，需要重新计算圆
                    center = point;
                    radius = 0;
                    for (int j = 0; j < i; j++) {
                        BlockPos prevPoint = shuffled.get(j);
                        if (!isInsideCircle(prevPoint, center, radius)) {
                            // 两点确定圆的直径
                            center = new BlockPos(
                                    (point.getX() + prevPoint.getX()) / 2,
                                    (point.getY() + prevPoint.getY()) / 2,
                                    (point.getZ() + prevPoint.getZ()) / 2
                            );
                            radius = distance(center, point);
                            for (int k = 0; k < j; k++) {
                                BlockPos oldPoint = shuffled.get(k);
                                if (!isInsideCircle(oldPoint, center, radius)) {
                                    // 三点确定圆（三维空间中为球）
                                    center = findCircumcenter(point, prevPoint, oldPoint);
                                    radius = distance(center, point);
                                }
                            }
                        }
                    }
                }
            }

            return center;
        }

        // 判断点是否在圆内
        private static boolean isInsideCircle(BlockPos point, BlockPos center, double radius) {
            return distance(point, center) <= radius + 1e-6; // 考虑浮点误差
        }

        // 计算两点之间的欧几里得距离
        private static double distance(BlockPos a, BlockPos b) {
            int dx = a.getX() - b.getX();
            int dy = a.getY() - b.getY();
            int dz = a.getZ() - b.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        // 计算三维空间中三点的外接圆心（最小覆盖球的圆心）
        private static BlockPos findCircumcenter(BlockPos a, BlockPos b, BlockPos c) {
            // 向量AB和AC
            int abX = b.getX() - a.getX();
            int abY = b.getY() - a.getY();
            int abZ = b.getZ() - a.getZ();
            int acX = c.getX() - a.getX();
            int acY = c.getY() - a.getY();
            int acZ = c.getZ() - a.getZ();

            // 叉积（法向量）
            int nx = abY * acZ - abZ * acY;
            int ny = abZ * acX - abX * acZ;
            int nz = abX * acY - abY * acX;

            // 平面方程: nx*(x - a.x) + ny*(y - a.y) + nz*(z - a.z) = 0
            // 圆心在AB和AC的中垂面的交线上（简化计算）
            int cx = (a.getX() + b.getX() + c.getX()) / 3;
            int cy = (a.getY() + b.getY() + c.getY()) / 3;
            int cz = (a.getZ() + b.getZ() + c.getZ()) / 3;

            return new BlockPos(cx, cy, cz);
        }
    }
}
