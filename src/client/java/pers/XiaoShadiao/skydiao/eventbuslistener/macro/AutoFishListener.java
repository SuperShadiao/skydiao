package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractFishingListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.mixin.client.MixinFishHookEntityHookedAccessor;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.*;
import java.util.function.Consumer;

public class AutoFishListener extends AbstractFishingListener implements IMacro {

    private static final double MAX_SIMULATE_OFFSET = 1.2;
    public boolean mythicalFishModeFlag;
    public boolean mythicalFishMode;

    private long lastThrowTime;

    private final AntiAFKThread antiAFKMove = new AntiAFKThread(AntiAFKThread.MOVE);
    private final AntiAFKThread antiAFKJump = new AntiAFKThread(AntiAFKThread.JUMP);
    private final AntiAFKThread antiAFKRotation = new AntiAFKThread(AntiAFKThread.ROTATION);
    private final LotusAtollJumpKeeper lotusAtollJumpKeeper = new LotusAtollJumpKeeper();

    private int catchFishCount;

    private boolean isEmergencyStopped;
    private int fishHookOnGroundTick;
    private int fishHookOnGroundCount;

    class AntiAFKThread extends Thread {

        public static final int
                JUMP = 0,
                MOVE = 1,
                ROTATION = 2;

        private final int type;
        private boolean flag;
        public boolean sneakFlag;

        AntiAFKThread(int type) {
            setName("AutoFish AntiAFK Thread");
            start();

            this.type = type;
        }

        @Override
        public void run() {

            while(true) {
                try {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                    }
                    ToolList.getInstance().log.info("AntiAFK called");
                    try {
                        Thread.sleep(4000 + ToolList.getInstance().random.nextInt(3500) + ToolList.getInstance().random.nextInt(500));
                    } catch (InterruptedException e) {
                    }

                    if (isHoldingFishRod() && !InputSimulator.isInventoryOpen()) {
                        if (type == MOVE) {
                            Consumer<Boolean> key = (flag = !flag) ? InputSimulator::setLeft : InputSimulator::setRight;

                            InputSimulator.setShift(true);

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                            }
                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen()) key.accept(true);

                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException e) {
                            }

                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen()) key.accept(false);

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                            InputSimulator.setShift(false);
                        } else if (type == JUMP) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }

                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setJump(true);

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                            }

                            InputSimulator.setJump(false);
                        } else if (type == ROTATION) {

                            float tempP = InputSimulator.getPlayerPitch();
                            float tempY = InputSimulator.getPlayerYaw();

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }

                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setPlayerPitch(tempP + 1.3453f * ToolList.getInstance().random.nextInt(3) * (ToolList.getInstance().random.nextBoolean() ? 1 : -1));
                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setPlayerYaw(tempY + 1.3453f * ToolList.getInstance().random.nextInt(3) * (ToolList.getInstance().random.nextBoolean() ? 1 : -1));

                            try {
                                Thread.sleep(2000 + ToolList.getInstance().random.nextInt(3000));
                            } catch (InterruptedException e) {
                            }

                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setPlayerPitch(tempP);
                            if (isHoldingFishRod() && !InputSimulator.isInventoryOpen())
                                InputSimulator.setPlayerYaw(tempY);

                        }
                    }
                } catch (Exception e) {

                }

            }
        }
    }

    class LotusAtollJumpKeeper extends Thread {

        LotusAtollJumpKeeper() {
            setName("Lotus Atoll Jump Keeper");

            start();
        }

        @Override
        public void run() {
            while(true) {
                try {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                    }
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 保持跳跃已激活, 若要关闭可在设置里的自动类关闭!"));
                    while (isHoldingFishRod() && !isEmergencyStopped) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                        InputSimulator.setJump(true);
                    }

                    InputSimulator.setJump(false);
                } catch (Exception e) {

                }

            }
        }

    }

    private boolean isThreadWorking;

    private void triggerFishHook() {
        if(System.currentTimeMillis() - lastThrowTime < ConfigManager.autofishDelayRetraction.getValue()) return;
        if(!isThreadWorking) this.interrupt();
    }

    @Override
    public void run() {
        while (true) {
            lastThrowTime = System.currentTimeMillis();
            isThreadWorking = false;
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {}
            try {
                executeThread();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private void executeThread() {

        isReady = false;
        isThreadWorking = true;
        if (isEmergencyStopped) {
            ensureHookUnsummoned();
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c当前自动钓鱼处于急停状态, 脱离AFK状态后将自动重新启用"));
            return;
        }
        if (ConfigManager.lotusAtollAutofishKeep.getValue() && isInLotusAtoll()) {
            lotusAtollJumpKeeper.interrupt();
        }
        if (!ConfigManager.autoFish.getValue() || InputSimulator.isInventoryOpen() || "kuudra".equals(StatusManager.get().getMode())) return;
        if (ToolList.getInstance().random.nextInt(3) == 1) {
            if(ConfigManager.autoFishAutoJump.getValue()) antiAFKJump.interrupt();
        } else if(ToolList.getInstance().random.nextInt(10) == 5) {
            if(ConfigManager.autoFishAutoMove.getValue()) antiAFKMove.interrupt();
        }
        if(catchFishCount++ % (2 + ToolList.getInstance().random.nextInt(2)) == 0) {
            if(ConfigManager.autoFishAutoRotation.getValue()) antiAFKRotation.interrupt();
        }

        activeThisMacro();
        InputSimulator.singleRightClick();

        while(true) {
            try {
                Thread.sleep(300);
                Thread.sleep(ConfigManager.autofishrethrowhookdelay.getValue());
                break;
            } catch (InterruptedException e) {

            }
        }

        InputSimulator.singleRightClick();

        if(!StatusManager.get().isInSkyblock()) {
            for (int i = 0; i < 20; i++) {
                if (mythicalFishModeFlag) break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }

            if (mythicalFishModeFlag) {
                mythicalFishModeFlag = false;
                mythicalFishMode = true;
                int count = 0;
                int currentCount = 10;
                FishingHook fishEntityFlag = mc.player.fishing;
                while (mythicalFishMode && mc.player != null && isHoldingFishRod()) {

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                    count++;

                    FishingHook fishEntity = mc.player.fishing;
                    if (fishEntity != fishEntityFlag) {
                        mythicalFishMode = false;
                        break;
                    }
                    List<ArmorStand> fish = mc.level.getEntitiesOfClass(ArmorStand.class, fishEntity.getBoundingBox().inflate(1.1));
                    fish.sort(Comparator.comparingDouble(e -> e.distanceTo(fishEntity)));
                    boolean flag = false;

                    for (ArmorStand e : fish) {
                        Component component = e.getName();
                        String nameNoColor = ToolList.getInstance().deleteColorCode(component.getString());
                        if (nameNoColor.matches("\\[\\|+\\]")) {
                            for (Component c : component.getSiblings()) {
                                String name = Optional.of(c.getStyle()).map(Style::getColor).map(TextColor::toString).orElse("");
                                if (name.equals("red")) {
                                    if (count != 0 && count % currentCount == 0) {
                                        currentCount = 7 + ToolList.getInstance().random.nextInt(6);
                                        count = 0;
                                        flag = true;
                                    } else {
                                        flag = false;
                                        break;
                                    }
                                    ;
                                }
                                if (name.equals("green")) {
                                    flag = true;
                                }
                            }

                        }
                    }
                    if (flag) {
                        InputSimulator.singleRightClick();
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
                ensureHookSummoned();
            }
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }
        ensureHookSummoned();
    }

    private void ensureHookSummoned() {
        while(isHoldingFishRod() && mc.player != null && mc.player.fishing == null) {
            InputSimulator.singleRightClick();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
        }
    }

    private void ensureHookUnsummoned() {
        while(isHoldingFishRod() && mc.player != null && mc.player.fishing != null) {
            InputSimulator.singleRightClick();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
        }
    }


    private boolean isHoldingFishRod() {
        if(mc.player == null) return false;
        return mc.player.getMainHandItem().getItem() == Items.FISHING_ROD && !mc.player.getMainHandItem().getHoverName().getString().contains("Carnival");
    }

    @Override
    public String getListenerName() {
        return "AutoFishListener";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::worldUnload);
        WorldRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(WorldRenderContext context) {
        if(mc.player == null || mc.player.fishing == null || mc.level == null || !ConfigManager.autoFish.getValue()) return;
        if(fakeFishHook != null && !isHookInLiquid() && lockedHookedEntity == null && mc.player.fishing.getHookedIn() != null) {
            RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            RenderUtils.renderESP(wr, fakeFishHook, 1, 0, 0, 1, false);
            RenderUtils.renderESP(wr, mc.player.fishing, 0, 1, 1, 1, false);
            RenderUtils.renderWorldLine(wr, fakeFishHook.position(), mc.player.fishing.position(),
                    1, 0, 0, 1,
                    0, 1, 1, 1);
            wr.finishDraw();
        }
    }

    private void worldUnload(Minecraft mc, ClientLevel level) {
        mythicalFishMode = mythicalFishModeFlag = false;
    }

    private int lastHookEntityId;

    private ArmorStand fishHookCarrier;
    private Entity lockedHookedEntity;
    private FishingHook fakeFishHook;
    private int failedToSimulateTick;
    private int failedToSimulateCounter;
    private Vec3 lastRecordFishHookMotion;

    private final List<Vec3> fakeFishHookPath = new ArrayList<>();

    private void onStartClientTick(Minecraft mc) {
        if(mc.player == null || mc.player.fishing == null || mc.level == null) {
            if(fakeFishHook != null && mc.level != null) {
                mc.level.removeEntity(fakeFishHook.getId(), Entity.RemovalReason.DISCARDED);
            }
            fakeFishHook = null;
            failedToSimulateTick = 0;
            lockedHookedEntity = null;
            fishHookCarrier = null;
            failedToSimulateCounter = 0;
            fakeFishHookPath.clear();
            return;
        }

        if(fakeFishHook == null) {
            fakeFishHook = ToolList.getInstance().cloneEntity(mc.player.fishing);
            fakeFishHook.setId(-999);
            fakeFishHook.setUUID(UUID.randomUUID());
            fakeFishHook.setInvisible(true);
            ((MixinFishHookEntityHookedAccessor) fakeFishHook).setHookedEntity0(null);
        }
        if(fakeFishHook != null) {
            fakeFishHook.setOnGround(false);
            fakeFishHook.setOldPosAndRot();
            for (int i = 0; i < (mc.player.fishing.distanceTo(fakeFishHook) > MAX_SIMULATE_OFFSET && fakeFishHook.getY() > mc.player.fishing.getY() == fakeFishHook.getDeltaMovement().y() < 0 ? 2 : 1); i++) {
                fakeFishHook.tick();
            }
            fakeFishHookPath.add(fakeFishHook.position());
            if(fakeFishHookPath.size() > 60) fakeFishHookPath.removeFirst();
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if(!(entity instanceof ArmorStand armorStand)) continue;

            if(mc.player.fishing.distanceTo(armorStand) < 2) {
                if(armorStand.getName().getString().contains("!!!")) {
                    if (lastHookEntityId != armorStand.getId()) {
                        lastHookEntityId = armorStand.getId();
                        triggerFishHook();
                        fishHookOnGroundTick = 0;
                        fishHookOnGroundCount = 0;
                        return;
                    }
                }
            }
        }

        if(lockedHookedEntity != null && !ToolList.getInstance().isEntityOnWorld(lockedHookedEntity)) lockedHookedEntity = null;
        Entity hooked = lockedHookedEntity != null ? lockedHookedEntity : mc.player.fishing.getHookedIn();
        if(hooked != null) {
            // class_1531['[Lv65] ⚓☮♃ gorF 33,000/40,000❤'/924037, l='ClientLevel', x=16.45, y=64.48, z=3.47]
            if(hooked instanceof ArmorStand carrier) fishHookCarrier = carrier;
            E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(hooked.asLivingEntity());
            if (mobInfo != null && mobInfo.armorStand.getName().getString().contains("❤") /* 包含❤符号证明是海怪, 自动重抛 */) {
                lockedHookedEntity = hooked;
                triggerFishHook();
            }
        }

        if(mc.player.fishing.onGround()) {
            fishHookOnGroundTick++;
            if(fishHookOnGroundTick > 60) {
                fishHookOnGroundTick = 0;
                fishHookOnGroundCount++;
                if(fishHookOnGroundCount > 3) {
                    fishHookOnGroundCount = 0;
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c警告: 当前鱼钩未能成功甩入水中, 已进入急停状态!"));
                    isEmergencyStopped = true;
                }
                triggerFishHook();
            }
        }
        if(fakeFishHook != null && !isHookInLiquid() && lockedHookedEntity == null && hooked != null) {
            double distanceSqr = fakeFishHookPath.stream().mapToDouble(v -> mc.player.fishing.distanceToSqr(v)).min().orElse(mc.player.fishing.distanceToSqr(fakeFishHook));
            if(distanceSqr > MAX_SIMULATE_OFFSET * MAX_SIMULATE_OFFSET) {
                failedToSimulateTick++;
                if(failedToSimulateTick > 5) {
                    failedToSimulateCounter++;
                    if(ToolList.getInstance().isXiaoShadiao()) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e鱼钩脱离正常的轨迹, 尝试第" + failedToSimulateCounter + "次校正..."));
                    if(failedToSimulateCounter > 1) {
                        if (isThisMacroEnabled()) {
                            AbstractListener.mml.triggerAlert("鱼钩脱离正常的轨迹, 疑似马口检查!");
                        } else {
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e注意: 请保证§c红色 (预测位置)§e 和§b青色 (鱼钩位置)§e 靠在一起, 防止Macro警报误触发."));
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e若成功靠在一起, 该消息不会弹出"));
                        }
                    }
                    if(fakeFishHook != null && lastRecordFishHookMotion != null && !lastRecordFishHookMotion.equals(Vec3.ZERO)) {
                        fakeFishHook.setPos(mc.player.fishing.position());
                        fakeFishHook.setDeltaMovement(lastRecordFishHookMotion);
                    }
                    failedToSimulateTick = -30;
                }
            } else {
                failedToSimulateTick = 0;
            }
        } else {
            failedToSimulateTick = 0;
        }

        isEmergencyStopped &= basicListener.isAFK();
    }

    private boolean isHookInLiquid() {
        if (mc.level == null || mc.player == null || mc.player.fishing == null) {
            return false;
        }
        if (!StatusManager.get().isInSkyblock()) {
            return mc.player.fishing.isInLiquid();
        }
        Vec3 posB = mc.player.fishing.position();
        Vec3 posA = posB.add(0, -0.3, 0);

        return mc.level.getFluidState(BlockPos.containing(posA)).is(FluidTags.WATER) || mc.level.getFluidState(BlockPos.containing(posB)).is(FluidTags.WATER)
        || mc.level.getFluidState(BlockPos.containing(posA)).is(FluidTags.LAVA) || mc.level.getFluidState(BlockPos.containing(posB)).is(FluidTags.LAVA);
    }

    private boolean isReady;

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(mc.level == null || mc.player == null) {
            isReady = false;
            return false;
        }
        Component title = ToolList.getInstance().tryGetTitleFromPacket(packet);
        if(title != null) {
            String str = ToolList.getInstance().deleteColorCode(title.getString());
            if (str.toLowerCase().matches("(.*)(mythical fish|神话鱼)(.*)")) {
                mythicalFishModeFlag = true;
            }
        } else if(packet instanceof ClientboundMoveEntityPacket movePacket && !StatusManager.get().isInSkyblock()) {
            Entity entity = movePacket.getEntity(mc.level);
            if(entity != null && mc.player.fishing == entity) {
                if(entity.isInLiquid()) {
                    short y = movePacket.getYa();
                    if(y > 0) isReady = true; else if(isReady && y < -300) {
                        isReady = false;
                        fishHookOnGroundTick = 0;
                        fishHookOnGroundCount = 0;
                        triggerFishHook();
                    }
                }
            }
        } else if(packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
            Entity entity = mc.level.getEntity(motionPacket.getId());
            if(entity != null) {
                if(mc.player.fishing == entity) {
                    // logger.info("Hook: " + motionPacket.getMovement());
                    lastRecordFishHookMotion = motionPacket.getMovement();
                }
            }
        }

        return false;
    }

    @Override
    public boolean isMacroActive() {
        return isHoldingFishRod();
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        boolean flag = mc.player != null && mc.player.fishing == null;
        if(flag) isEmergencyStopped = true;
        return flag;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        boolean flag = mc.player != null && mc.player.fishing == null;
        if(flag) isEmergencyStopped = true;
        return flag;
    }

    @Override
    public String getMacroName() {
        return "Auto Fish";
    }

}
