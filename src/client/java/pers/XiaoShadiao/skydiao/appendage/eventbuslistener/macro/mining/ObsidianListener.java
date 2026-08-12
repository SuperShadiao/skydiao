package pers.XiaoShadiao.skydiao.appendage.eventbuslistener.macro.mining;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import pers.XiaoShadiao.skydiao.appendage.utils.posrecord.RecordPos;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.IMacro;
import pers.XiaoShadiao.skydiao.appendage.utils.posrecord.PositionList;
import pers.XiaoShadiao.skydiao.appendage.utils.hitresult.PUtil;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ObsidianListener extends AbstractListener implements IMacro {

    public static PositionList plist = null;

    private static final Map<Vec3i, Integer> piggod = gomap(List.of(
            new Vec3i(1, 0, 0), new Vec3i(-1, 0, 0),
            new Vec3i(0, 1, 0), new Vec3i(0, -1, 0),
            new Vec3i(0,0,1), new Vec3i(0,0,-1)
    ));
    private static final List<Vec2> draem = List.of(
            new Vec2(0.0625f, 0.0625f), new Vec2(0.0625f, 0.9375f), new Vec2(0.9375f, 0.9375f), new Vec2(0.9375f, 0.0625f)
    );
    private static final List<Block> airs = List.of(Blocks.AIR, Blocks.CAVE_AIR);
    private static List<Block> targets = List.of(Blocks.OBSIDIAN);
    private static final Random random = new Random();

    private static Set<BlockPos> MblockPoss = new HashSet<>();//用于测试
    private static BlockPosWithFace selected;
    private static final Vec2[] psvecs = new Vec2[2];
    public static Status status = Status.DISABLED;

    private static boolean isstartyet = false;
    private static boolean isObsidianReady = false;
    private static boolean isready = false;

    private static int targetIndex = 0;
    private static int moveRule = 0;

    private static int cooldown = 0;
    public static int lanternTicks = 270*20;
    public static int lanternTimer = 0;
    private static int lanternTaskTimer = 0;
    private static int slotSwitchedTimer = 0;
    private static int retryPressTimer = 0;
    private static int selectedNullTicks = 0;
    private static final int smoothTicks = 2;
    private static int smoothTimer = 0;

    private static <E> Map<E, Integer> gomap(List<E> list){
        Map<Integer, E> map = list.stream().collect(Collectors.toMap(list::indexOf, Function.identity()));
        Map<E, Integer> result = new HashMap<>();
        for(Integer i : map.keySet()){
            result.put(map.get(i), i);
        }
        return result;
    }

    private static MutableComponent mp(){
        return getGradientComponent("[AutoObsidian]", 0x58f9eb, 0x71f958).append(" ");
    }

    @Override
    public String getListenerName() {
        return "AutoObsidian";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.player == null || !ConfigManager.iAutoObsidian.getValue() || mc.level == null ) return;
        if(MblockPoss.isEmpty()) return;

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for(BlockPos pos : MblockPoss){
            RenderUtils.renderESP(wr, pos, 0, 0.5f, 0.5f, 1, false);
        }
        //渲染所选方块
        RenderUtils.renderESP(wr, selected, 0.5f, 0, 0.5f, 1, false);
        wr.finishDraw();

        //渲染目标位置
        wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        Vec3 vec3 = plist.positions().get(targetIndex%plist.positions().size());
        RenderUtils.renderESP(wr, vec3.x-0.15,vec3.y+0.85,vec3.z-0.15,vec3.x+0.15,vec3.y+1.15,vec3.z+0.15, 1f, 0.4f, 1f, 0.5f, true);
        wr.finishDraw();

    }

    @Override
    public boolean isMacroActive() {
        return ConfigManager.iAutoObsidian.getValue();
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        return true;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        return true;
    }

    @Override
    public String getMacroName() {
        return "AutoObsidian";
    }


    private void onStartClientTick(Minecraft mc) {
        if(mc.player == null || mc.level == null ) return;
        if (ConfigManager.iAutoObsidian.getValue()) {
            if(!isstartyet) {
                PositionList pl = RecordPos.getLocal(ConfigManager.autoObsidianPositionsFile.getValue());
                if(pl==null) {
                    Minecraft.getInstance().player.sendSystemMessage(mp().append(Component.literal(giwk("positionsfilenotfind")).withColor(0xf22b30)));
                    ConfigManager.iAutoObsidian.setValue(false);
                    return;
                }
                else plist = pl;
            }
            if(plist.positions().size()<=1){
                Minecraft.getInstance().player.sendSystemMessage(mp().append(Component.literal(giwk("positioncounterror")).withColor(0xf22b30)));
                ConfigManager.iAutoObsidian.setValue(false);
                return;
            }
            status = Status.ACTIVE;


            if(lanternTimer >= 1){
                lanternTimer--;
                mineObsidian();
            }else {
                placeLantern();
            }
            isstartyet = true;
        } else if (isstartyet) {
            //LoggerFactory.getLogger(ObsidianListener.class).info("!");
            stopActions();
            isstartyet = false;
            isready = false;
            isObsidianReady = false;
            moveRule = 0;
            lanternTaskTimer = 0;
            retryPressTimer = 0;
            if(lanternTimer >= 1){
                lanternTimer--;
            }
        }else if(status == Status.ACTIVE){
            status = Status.DISABLED;
            if(lanternTimer >= 1){
                lanternTimer--;
            }
        }else if(lanternTimer >= 1){
            lanternTimer--;
        }
    }

    private void mineObsidian(){

        Player player = Minecraft.getInstance().player;
        Vec3 ppos = player.getEyePosition(1.0f);

        //1.1. 准备工作
        if(!isready){
            targetIndex = findNearest(ppos);
            isready = true;
        }

        if(!isObsidianReady){
            cooldown = 0;
            selected = null;
            smoothTimer = 0;
            selectedNullTicks = 0;
            isObsidianReady = true;
            lanternTaskTimer = 0;
            slotSwitchedTimer = 0;
            retryPressTimer = 0;
            InputSimulator.switchItem(ConfigManager.drillSlot.getValue());
        }

        //1.2.物品栏校验
        if(player.getInventory().getSelectedSlot()!=ConfigManager.drillSlot.getValue()){
            slotSwitchedTimer++;
            if(slotSwitchedTimer >= 20){
                ConfigManager.iAutoObsidian.setValue(false);
                mc.player.sendSystemMessage(mp().append(Component.literal(giwk("slotswitched")).withColor(0xf22b30)));
                slotSwitchedTimer = 0;
                status = Status.SLOT_SWITCHED;
                return;
            }
        }else {
            slotSwitchedTimer = 0;
        }

        //1.3. 距离检测
        if(ppos.distanceTo(plist.positions().get(targetIndex))>=20){
            ConfigManager.iAutoObsidian.setValue(false);
            mc.player.sendSystemMessage(mp().append(Component.literal(String.format(giwk("sofaraway"),String.valueOf(20))).withColor(0xf22b30)));
            status = Status.SO_FAR_AWAY;
        }

        //目标点vec3
        Vec3 tpos = plist.positions().get(targetIndex);

        //2.1. 判断是否到达目标范围内. 若是,目标变为下一个点.
        if(xzDistance(tpos, ppos) <= 1.75d){
            targetIndex = (targetIndex + 1)%plist.positions().size();
        }

        //玩家朝向(水平角)
        double angle0 = mod360(player.getYHeadRot());

        //与目标方向夹角
        double angle1 = mod360(getdegree(tpos.x-ppos.x, tpos.z-ppos.z)-90);

        //所选为空的处理措施
        if(selected == null){
            selectedNullTicks++;
            InputSimulator.setPlayerYaw((float) angle1);
            InputSimulator.setPlayerPitch(random.nextFloat()*-10);
            ISH(false,true,false);
            if(selectedNullTicks >= 20){
                ConfigManager.iAutoObsidian.setValue(false);
                mc.player.sendSystemMessage(mp().append(Component.literal(giwk("blocknotfound")).withColor(0xf22b30)));
                selectedNullTicks = 0;
                status = Status.SUITABLE_BLOCK_NOT_FOUND;
                return;
            }
        }

        //冷却高于0的处理措施
        if(cooldown>0){
            cooldown-=1;

            if(!(cooldown>=ConfigManager.obsidianCycleTicks.getValue()-1&&retryPressTimer==12)){
                InputSimulator.pressLeftClick();
            }
            if(selected != null){

                //重新规划移动按键
                if(cooldown == ConfigManager.obsidianCycleTicks.getValue()/2){
                    int u = 0;
                    double dAngle = 180;
                    for(int i=-2; i<=2; i++){
                        double da = Math.abs(mod360(angle0+i*45-angle1));
                        if(da<dAngle){
                            dAngle = da;
                            u = i;
                        }
                    }
                    moveRule = u;
                }

                move(moveRule);

                //平滑过渡
                if(smoothTimer>=1 && psvecs[0]!=null && psvecs[1]!=null){
                    setTickPitchYaw();
                    smoothTimer--;
                }else {
                    Vec2 vector2 = selected.getPitchYaw();
                    InputSimulator.setPlayerPitch(vector2.x);
                    InputSimulator.setPlayerYaw(vector2.y);
                }
            }

            return;
        }

        //备份所选方块
        BlockPosWithFace selectedBackup = null;
        if(selected!=null){
            selectedBackup = new BlockPosWithFace(selected);
        }
        selected = null;

        //2.2. 缩范围
        //deleted
        //2.3. 寻找方块

        Map<BlockPos, Byte> targetBlocksMap = findBlocksByAir(ppos, 5, targets);

        //2.3.1. 清掉不在角度范围、有可能因延迟错选的方块
        for(BlockPos blockpos : new HashSet<>(targetBlocksMap.keySet())){
            Vec3 centerPos = Vec3.atCenterOf(blockpos);
            double angle2 = mod360(getdegree(centerPos.x-ppos.x, centerPos.z-ppos.z)-90);

            boolean b1 = Math.abs(mod360(angle2-angle1)) < 60;

            //排除有可能因延迟错选的方块
            if(selectedBackup!=null) b1 = b1 && !isInCube(selectedBackup, 2, blockpos);
            if (!b1) targetBlocksMap.remove(blockpos);
        }

        MblockPoss = new HashSet<>();

        if(targetBlocksMap.isEmpty()) {
            return;
        }

        //2.3.2. 面筛选
        for(BlockPos blockpos : new HashSet<>(targetBlocksMap.keySet())){
            //面的存储值
            byte nb = (byte)0;

            for(int i = 0; i < 6; i++){
                if( ((targetBlocksMap.get(blockpos) >> i) & 1) == 0) continue;

                int t1 = i/2;
                int t2 = i%2;


                //变换,将x/y/z提到第一位
                Vec3 trv3 = transform(Vec3.atLowerCornerOf(blockpos), t1);
                //获取探测坐标列表
                List<Vec2> checkVec2 = getCheckVec2((int) Math.round(trv3.y), (int) Math.round(trv3.z));
                boolean b1 = true;
                for(Vec2 vec2 : checkVec2){
                    //还原
                    Vec3 tv3 = transform(new Vec3(trv3.x + t2, vec2.x, vec2.y), -t1);

                    //竖直角
                    float an1 = (float) -Math.toDegrees(
                            Math.atan(
                                    (tv3.y-ppos.y) / xzDistance(ppos, tv3)
                            )
                    );

                    if(Math.abs(an1)>=40){ b1 = false;break;}

                    //水平角
                    float an2 = (float) mod360(getdegree(tv3.x -ppos.x, tv3.z -ppos.z)-90);

                    //探测面的可观测性
                    HitResult result = PUtil.hitResultByAngle(
                            mc.player,
                            an1,
                            an2
                    );

                    //排除：hitresut不为方块
                    if(!(result instanceof BlockHitResult)){
                        b1 = false;break;
                    }

                    //排除：hitresult观测失败
                    if(
                            result.getLocation().distanceTo(tv3) >= 0.02
                    ){
                        b1 = false;break;
                    }
                }

                if(b1) {
                    nb = (byte) (nb | (1 << i));
                }
            }
            if(nb==(byte)0) targetBlocksMap.remove(blockpos);
            else {
              targetBlocksMap.put(blockpos, nb);
            }
        }

        MblockPoss = targetBlocksMap.keySet();

        if(targetBlocksMap.isEmpty()) {
            return;
        }

        //2.3.3. 看能否分离距离>=3.0的方块
        Map<BlockPos, Byte> _targetBlocksMap = new HashMap<>();
        for(BlockPos blockpos : new HashSet<>(targetBlocksMap.keySet())){
            Vec3 centerPos = Vec3.atCenterOf(blockpos);
            if (!(ppos.distanceTo(centerPos) >= 3.0)) {
                _targetBlocksMap.put(blockpos, targetBlocksMap.get(blockpos));
                targetBlocksMap.remove(blockpos);
            }
        }

        if(targetBlocksMap.isEmpty()) {
            targetBlocksMap = new HashMap<>(_targetBlocksMap);
        }
        _targetBlocksMap = new HashMap<>();

        //2.3.4. 最终筛选
        //较大的数
        double d = 1024;
        BlockPosWithFace blockPosWithFace = null;
        BlockPos _bp = null;
        for(BlockPos blockpos : targetBlocksMap.keySet()){
            for(int i = 0; i < 6; i++){

                if( ((targetBlocksMap.get(blockpos) >> i) & 1) == 0) continue;

                int t1 = i/2;
                int t2 = i%2;

                //变换,将x/y/z提到第一位
                Vec3 trv3 = transform(Vec3.atLowerCornerOf(blockpos), t1);

                Vec2 vec2 = new Vec2(Math.round(trv3.y)+0.5f, Math.round(trv3.z)+0.5f);
                //还原
                Vec3 tv3 = transform(new Vec3(trv3.x + t2, vec2.x, vec2.y), -t1);

                float an1 = (float) -Math.toDegrees(
                        Math.atan(
                                (tv3.y-ppos.y) / xzDistance(ppos, tv3)
                        )
                );

                float an2 = (float) Math.abs(mod360(getdegree(tv3.x -ppos.x, tv3.z -ppos.z)-90-angle1));

                //依据: 比较水平角与目标相对距离与竖直角绝对值之和的大小
                if(Math.abs(an1)+an2<d) {
                    d = Math.abs(an1)+an2;
                    blockPosWithFace = new BlockPosWithFace(blockpos, i);
                    _bp = blockpos;
                }
            }
        }

        if(blockPosWithFace==null) return;

        //2.3.5. 依据对角线"距离"转换面
        double dt = 0;
        for(int i = 0; i < 6; i++){
            if( ((targetBlocksMap.get(_bp) >> i) & 1) == 0) continue;
            int t1 = i/2;
            int t2 = i%2;

            //变换,将x/y/z提到第一位
            Vec3 trv3 = transform(Vec3.atLowerCornerOf(blockPosWithFace), t1);

            Vec2 vec21 = new Vec2(Math.round(trv3.y)+0.1f, Math.round(trv3.z)+0.1f);
            Vec2 vec22 = new Vec2(Math.round(trv3.y)+0.9f, Math.round(trv3.z)+0.9f);
            //还原
            Vec3 tv31 = transform(new Vec3(trv3.x + t2, vec21.x, vec21.y), -t1);
            Vec3 tv32 = transform(new Vec3(trv3.x + t2, vec22.x, vec22.y), -t1);

            float an11 = (float) -Math.toDegrees(
                    Math.atan(
                            (tv31.y-ppos.y) / xzDistance(ppos, tv31)
                    )
            );
            float an12 = (float) -Math.toDegrees(
                    Math.atan(
                            (tv32.y-ppos.y) / xzDistance(ppos, tv32)
                    )
            );

            float an21 = (float) Math.abs(mod360(getdegree(tv31.x -ppos.x, tv31.z -ppos.z)-90-angle1));
            float an22 = (float) Math.abs(mod360(getdegree(tv32.x -ppos.x, tv32.z -ppos.z)-90-angle1));
            double dt1 = Math.sqrt(Math.pow(an11 - an12, 2) + Math.pow(an21 - an22, 2));
            if(dt1>dt) {
                dt=dt1;
                blockPosWithFace.face = i;
            }
        }


        selected = blockPosWithFace;
        //LoggerFactory.getLogger(ObsidianListener.class).info("seleted=" + selected + selected.face);


        Vec2 vec2_ = blockPosWithFace.getPitchYaw();

        float SAngle1 = vec2_.x;

        float SAngle2 = vec2_.y;

        //2.4. 转向,移动

        //2.4.1. 寻找移动方向
        int u = 0;
        double dAngle = 180;

        for(int i=-2; i<=2; i++){
            double da = Math.abs(mod360(SAngle2+i*45-angle1));
            if(da<dAngle){
                dAngle = da;
                u = i;
            }
        }

        moveRule = u;

        //2.4.2. "平滑"衔接
        smoothTimer = smoothTicks;

        psvecs[0] = new Vec2(player.getXRot(), player.getYHeadRot());
        psvecs[1] = new Vec2(SAngle1, SAngle2);

        setTickPitchYaw();

        if(retryPressTimer == 0){
            InputSimulator.releaseLeftClick();
            retryPressTimer = 12;
        }else {
            InputSimulator.pressLeftClick();
            retryPressTimer--;
        }

        //计时器
        cooldown += ConfigManager.obsidianCycleTicks.getValue();
        smoothTimer--;
        selectedNullTicks = 0;


    }

    private void placeLantern(){
        if(lanternTaskTimer <= 0){
            stopActions();
        }

        lanternTaskTimer++;

        switch (lanternTaskTimer){
            case 5:
                InputSimulator.setJump(true);
                break;
            case 8:
                InputSimulator.setJump(false);
                break;
            case 12:
                InputSimulator.switchItem(ConfigManager.lanternSlot.getValue());
                break;
            case 14:
                InputSimulator.pressRightClick();
                break;
            case 16:
                InputSimulator.releaseRightClick();
                break;
            case 23:
                InputSimulator.switchItem(ConfigManager.drillSlot.getValue());
                break;
            default:
        }

        if(lanternTaskTimer >= 45){
            stopActions();
            lanternTimer = Math.toIntExact(Math.round(randomDouble(lanternTicks, 15*20)));
            mc.player.sendSystemMessage(mp().append(Component.literal(String.format(giwk("placelantern"),String.valueOf(Math.round((double) lanternTimer/20)))).withColor(0xd672de)));
            lanternTaskTimer = 0;

        }

    }

    public int findNearest(Vec3 vec){
        List<Vec3> p = plist.positions();
        double d = xzDistance(p.getFirst(), vec);
        int i = 0;
        for(int j = 1; j < p.size(); j++){
            if(xzDistance(p.get(j), vec) < d){
                d = xzDistance(p.get(j), vec);
                i = j;
            }
        }
        return i;
    }

    public static double xzDistance(Vec3 v1, Vec3 v2){
        return Math.sqrt(Math.pow(v2.x - v1.x, 2) + Math.pow(v2.z - v1.z, 2));
    }

    /**
     * 范围:[-180,180)
     * @param d double
     * @return result
     */
    public static double mod360(double d){
        return d - Math.floor((d+180)/360)*360;
    }

    private static double randomDouble(double center, double range){
        return center + 2*(random.nextDouble()-0.5)*range;
    }

    public static double getAvgMod360(double x, double y, double a){
        double x2 = mod360(x);
        double y2 = mod360(y);
        if(a <0 || a >1) return (x2+y2)/2;
        List<Double> arr = List.of(y2-360-x2, y2-x2, y2+360-x2);
        double d = 400;
        int index = 0;
        for(int j = 0; j < arr.size(); j++){
            if(Math.abs(arr.get(j)) <= d){
                index = j;
                d = Math.abs(arr.get(j));
            }
        }
        return mod360(x+a*arr.get(index));
    }

    //空气筛选
    private Map<BlockPos, Byte> findBlocksByAir(Vec3 center, int radius, List<Block> targetBlocks){
        //附近空气相对于方块: 0 x- ; 1 x+ ; 2 y- ; 3 y+ ; 4 z- ; 5 z+ ;(数字<i>为byte的数位,<i>为1代表存在)
        Map<BlockPos, Byte> map = new HashMap<>();

        BlockPos centerblock = BlockPos.containing(center);
        if (mc.level == null || !airs.contains(mc.level.getBlockState(centerblock).getBlock())) return map;

        //复杂度:O(R^3)

        HashSet<BlockPos> usedAir = new HashSet<>();
        Deque<BlockPos> usingAir = new ArrayDeque<>();
        usedAir.add(centerblock);
        usingAir.add(centerblock);

        while(!usingAir.isEmpty()){
            BlockPos current = usingAir.poll();
            //6面检查
            for(Vec3i vec3i : piggod.keySet()){

                BlockPos next = current.offset(vec3i);
                if(usedAir.contains(next)) continue;

                //排除超过半径方块
                //distToCenterSqr: 距离的平方
                if(next.distToCenterSqr(center) > radius*radius) continue;

                Block block = mc.level.getBlockState(next).getBlock();

                if(airs.contains(block)){
                    //复杂度:O(1)
                    usedAir.add(next);
                    usingAir.add(next);
                    continue;
                }
                //目标方块检测
                if(targetBlocks.contains(block)){
                    byte b = map.getOrDefault(next, (byte)0);
                    map.put(next, (byte) (b | (1 << piggod.get(vec3i))));
                }
            }

        }

        return map;
    }

    private static boolean isInCube(BlockPos pos1, int radius, BlockPos pos2){
        if(radius <= 0) return false;
        return isInLine(pos1.getX(), radius, pos2.getX())
                && isInLine(pos1.getY(), radius, pos2.getY())
                && isInLine(pos2.getZ(), radius, pos2.getZ());
    }

    private static boolean isInLine(int a, int r, int b){
        return Math.abs(a-b) <= r;
    }

    private static double getdegree(double x, double y){
        return Math.toDegrees(Math.atan2(y, x));
    }

    private List<Vec2> getCheckVec2(int p, int q){
        List<Vec2> list = new ArrayList<>();
        for (Vec2 vec2 : draem){
            Vec2 av2 = avgVec2(p, q, p+1, q+1, vec2);
            list.add(av2);
        }

        return list;
    }

    private Vec2 avgVec2(float v1x, float v1y, float v2x, float v2y, Vec2 avgVec){
        return avgVec2(v1x, v1y, v2x, v2y, avgVec.x, avgVec.y);
    }

    private Vec2 avgVec2(float v1x, float v1y, float v2x, float v2y, float fa, float fb){
        return new Vec2(fa*v1x+(1-fa)* v2x, fb*v1y+(1-fb)* v2y);
    }

    /**
     * (x,y,z)->(y,z,x)->(z,x,y)->(x,y,z)
     * @param vec3 vec3
     * @param time time
     * @return vec3
     */
    private static Vec3 transform(Vec3 vec3, int time){
        int t;
        if(time<0) t = ((time % 3)+3)%3;
        else t = time % 3;
        if(t == 0) return vec3;
        if(t == 1) return transform(vec3);
        //if t==2
        return transform(transform(vec3));
    }

    private static Vec3 transform(Vec3 vec3){
        return new Vec3(vec3.y, vec3.z, vec3.x);
    }

    public static class BlockPosWithFace extends BlockPos {
        //附近空气相对于方块: 0 x- ; 1 x+ ; 2 y- ; 3 y+ ; 4 z- ; 5 z+ ;
        public int face;
        public BlockPosWithFace(int x, int y, int z, int face) {
            super(x, y, z);
            this.face = face;
        }

        public BlockPosWithFace(BlockPos blockPos, int face) {
            super(blockPos);
            this.face = face;
        }

        public BlockPosWithFace(BlockPosWithFace blockPosWithFace){
            this(blockPosWithFace, blockPosWithFace.face);
        }

        public Vec2 getPitchYaw(){
            Player player = Minecraft.getInstance().player;
            Vec3 ppos = player.getEyePosition(1.0f);

            int st1 = face/2;
            int st2 = face%2;

            Vec3 Strv3 = transform(Vec3.atLowerCornerOf(this), st1);

            Vec2 Svec2 = new Vec2(Math.round(Strv3.y)+0.5f, Math.round(Strv3.z)+0.5f);
            //还原
            Vec3 Stv3 = transform(new Vec3(Strv3.x + st2, Svec2.x, Svec2.y), -st1);

            float SAngle1 = (float) -Math.toDegrees(
                    Math.atan(
                            (Stv3.y-ppos.y) / xzDistance(ppos, Stv3)
                    )
            );

            float SAngle2 = (float) mod360(getdegree(Stv3.x -ppos.x, Stv3.z -ppos.z)-90);

            return new Vec2(SAngle1, SAngle2);
        }

    }

    public Vec2 getRandomPitchYaw(Vec2 p, Vec2 s, double center, double range){
        return new Vec2(
                (float) getAvgMod360(p.x, s.x, randomDouble(center,range)),
                (float) getAvgMod360(p.y, s.y, randomDouble(center,range))
        );
    }

    public void setTickPitchYaw(){
        Vec2 vec2_1 = getRandomPitchYaw(
                psvecs[0], psvecs[1],
                ((double) smoothTimer) /(smoothTicks+1),
                0.15d/(smoothTicks+1)
        );

        InputSimulator.setPlayerPitch(vec2_1.x);
        InputSimulator.setPlayerYaw(vec2_1.y);
    }

    public void move(int i){
        switch (i){
            case -2:
                ISH(true,false,false);
                break;
            case -1:
                ISH(true,true,false);
                break;
            case 1:
                ISH(false,true,true);
                break;
            case 2:
                ISH(false,false,true);
                break;
            default:
                ISH(false,true,false);
        }
    }

    /**
     * 操作器
     * @param l press A
     * @param f press W
     * @param r press D
     */
    public void ISH(boolean l, boolean f, boolean r){
        InputSimulator.setLeft(l);
        InputSimulator.setForward(f);
        InputSimulator.setLeft(r);
    }

    private void stopActions(){
        InputSimulator.setForward(false);
        InputSimulator.setBackward(false);
        InputSimulator.setLeft(false);
        InputSimulator.setRight(false);
        InputSimulator.releaseLeftClick();
    }


    public enum Status{
        ACTIVE,
        SUITABLE_BLOCK_NOT_FOUND,
        SLOT_SWITCHED,
        DISABLED,
        SO_FAR_AWAY
    }

    public static String giwk(String key){
        return CrowdinI18nManager.translate("features.autoobsidian."+key);
    }

    public static MutableComponent getGradientComponent(String text, int color1, int color2){
        int len = text.length();
        MutableComponent component = Component.literal("");
        int i = 0;
        for(char c : text.toCharArray()){
            component.append(Component.literal(String.valueOf(c)).withColor(interpolateColor(color1, color2, (float) i/(len-1))));
            i++;
        }
        return component;
    }

    public static int interpolateColor(int startColor, int endColor, float fraction) {
        // 限制fraction范围在0到1之间
        fraction = Math.min(1f, Math.max(0f, fraction));

        // 提取各通道（ARGB顺序）
        int aStart = (startColor >> 24) & 0xFF;
        int rStart = (startColor >> 16) & 0xFF;
        int gStart = (startColor >> 8) & 0xFF;
        int bStart = startColor & 0xFF;

        int aEnd = (endColor >> 24) & 0xFF;
        int rEnd = (endColor >> 16) & 0xFF;
        int gEnd = (endColor >> 8) & 0xFF;
        int bEnd = endColor & 0xFF;

        // 线性插值
        int a = (int) (aStart + (aEnd - aStart) * fraction);
        int r = (int) (rStart + (rEnd - rStart) * fraction);
        int g = (int) (gStart + (gEnd - gStart) * fraction);
        int b = (int) (bStart + (bEnd - bStart) * fraction);

        // 重新组合成ARGB int
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

}
