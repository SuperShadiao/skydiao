package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quiz（Oruo the Omniscient）谜题：根据问题在聊天栏里找出正确选项，把对应答案方块周围用绿色标出来。
 *
 * <p>搬运自 Skyblocker 的 {@code Trivia}。题目和三个选项都来自聊天栏，答案表是静态的；
 * 另外 "What SkyBlock year is it?" 按 Skyblock 历法实时计算。
 *
 * <p>注意：Skyblocker 里关于仙女之魂数量的几个问题依赖它自己的 FairySouls 数据，本 mod 没有这份数据，
 * 这几个问题暂时不会给出答案。
 */
public final class TriviaSolver extends DungeonPuzzleSolver {

    /** 组 1 是问题，组 2/3 是选项字母和选项文本，组 4 是雕像的结算消息。 */
    private static final Pattern PATTERN = Pattern.compile(
            "^ +(?:([A-Za-z,' ]*\\?)| ([ⓐⓑⓒ]) ([a-zA-Z0-9 ]+))"
                    + "|(\\[STATUE] Oruo the Omniscient: (\\w+ answered Question #\\d correctly!|I bestow upon you all the power of a hundred years!|Yikes))$");

    private static final BlockPos CHOICE_A = new BlockPos(20, 70, 6);
    private static final BlockPos CHOICE_B = new BlockPos(15, 70, 9);
    private static final BlockPos CHOICE_C = new BlockPos(10, 70, 6);
    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** 仙女魂总数由网站上的 json 同步，格式是 {@code {"The End": 12, ...}}。 */
    private static final String FAIRY_SOULS_URL = "https://xiaoshadiao.club/sitesources/assets/skyblock/fairy_souls.json";
    private static final String TOTAL_FAIRY_SOULS_QUESTION = "How many total Fairy Souls are there?";
    private static final String FAIRY_SOULS_QUESTION_PREFIX = "How many Fairy Souls are there in ";
    private static volatile Map<String, Integer> fairySouls;
    private static volatile boolean fairySoulsLoading;
    /** 进入房间时要求拉取数据，失败要提示；如果拉取早就在进行，标记一下让那次结果负责提示。 */
    private static volatile boolean notifyFailure;

    private static final Map<String, List<String>> ANSWERS = new HashMap<>();

    static {
        ANSWERS.put("What is the status of The Watcher?", List.of("Stalker"));
        ANSWERS.put("What is the status of Bonzo?", List.of("New Necromancer"));
        ANSWERS.put("What is the status of Scarf?", List.of("Apprentice Necromancer"));
        ANSWERS.put("What is the status of The Professor?", List.of("Professor"));
        ANSWERS.put("What is the status of Thorn?", List.of("Shaman Necromancer"));
        ANSWERS.put("What is the status of Livid?", List.of("Master Necromancer"));
        ANSWERS.put("What is the status of Sadan?", List.of("Necromancer Lord"));
        ANSWERS.put("What is the status of Maxor?", List.of("The Wither Lords"));
        ANSWERS.put("What is the status of Goldor?", List.of("The Wither Lords"));
        ANSWERS.put("What is the status of Storm?", List.of("The Wither Lords"));
        ANSWERS.put("What is the status of Necron?", List.of("The Wither Lords"));
        ANSWERS.put("What is the status of Maxor, Storm, Goldor, and Necron?", List.of("The Wither Lords"));
        ANSWERS.put("Which brother is on the Spider's Den?", List.of("Rick"));
        ANSWERS.put("What is the name of Rick's brother?", List.of("Pat"));
        // 完整问题是 "What is the name of the vendor in the Hub who sells stained glass?"，
        // 但聊天栏多行显示时求解器只能看到最后一行 "glass?"
        ANSWERS.put("glass?", List.of("Wool Weaver"));
        ANSWERS.put("What is the name of the person that upgrades pets?", List.of("Kat"));
        ANSWERS.put("What is the name of the lady of the Nether?", List.of("Elle"));
        ANSWERS.put("Which villager in the Village gives you a Rogue Sword?", List.of("Jamie"));
        ANSWERS.put("How many unique minions are there?", List.of("61 Minions"));
        ANSWERS.put("Which of these enemies does not spawn in the Spider's Den?", List.of(
                "Zombie Spider", "Cave Spider", "Wither Skeleton", "Dashing Spooder", "Broodfather", "Night Spider"));
        ANSWERS.put("Which of these monsters only spawns at night?", List.of("Zombie Villager", "Ghast"));
        ANSWERS.put("Which of these is not a dragon in The End?", List.of(
                "Zoomer Dragon", "Weak Dragon", "Stonk Dragon", "Holy Dragon", "Boomer Dragon",
                "Booger Dragon", "Older Dragon", "Elder Dragon", "Stable Dragon", "Professor Dragon"));
    }

    /** 题目答案和当前高亮是全局状态：Quiz 可以隔着房间参与、半途离开后再回来。 */
    private static List<String> solutions = Collections.emptyList();
    private static String currentSolution = "";
    /** 这一次进房间是否已经检查过数据是否拉取。 */
    private boolean checkedThisRoom;

    public TriviaSolver() {
        super("trivia-room", "trivia");
        ensureFairySouls(false);
    }

    @Override
    public boolean receivesMessagesOutsideRoom() {
        return true;
    }

    @Override
    public void tick(Minecraft mc, RoomMatcher.Match room) {
        // 每次进房间都确认一下仙女魂数据拉过没有，没有就补一次
        if (checkedThisRoom) return;
        checkedThisRoom = true;
        ensureFairySouls(true);
    }

    @Override
    public boolean onGameMessage(Component message, boolean overlay, RoomMatcher.Match room) {
        if (overlay) return true;
        Matcher matcher = PATTERN.matcher(ChatFormatting.stripFormatting(message.getString()));
        if (!matcher.matches()) return true;

        // 题目答完/失败/通关时雕像会说话，清掉当前高亮
        if (matcher.group(4) != null) {
            clearCurrentSolution();
            return true;
        }

        String answerChoice = matcher.group(3);
        if (answerChoice == null) {
            // 这行是题目：先换答案表，再清掉上一题的高亮
            updateSolutions(matcher.group(0));
            clearCurrentSolution();
        } else if (!solutions.isEmpty() && solutions.contains(answerChoice)) {
            // 这行是正确选项，记下选项字母（ⓐ/ⓑ/ⓒ）
            currentSolution = matcher.group(2);
        }
        return true;
    }

    private void clearCurrentSolution() {
        currentSolution = "";
    }

    private void updateSolutions(String question) {
        String trimmed = question.trim();
        if (trimmed.equals("What SkyBlock year is it?")) {
            solutions = List.of("Year " + skyblockYear());
            return;
        }

        if (fairySouls == null) ensureFairySouls(false);
        Map<String, Integer> souls = fairySouls;
        if (souls != null) {
            if (trimmed.equals(TOTAL_FAIRY_SOULS_QUESTION)) {
                int total = souls.values().stream().mapToInt(Integer::intValue).sum();
                solutions = List.of(total + " Fairy Souls");
                return;
            }
            if (trimmed.startsWith(FAIRY_SOULS_QUESTION_PREFIX) && trimmed.endsWith("?")) {
                String region = trimmed.substring(FAIRY_SOULS_QUESTION_PREFIX.length(), trimmed.length() - 1);
                Integer total = souls.get(region);
                if (total == null && region.startsWith("The ")) {
                    total = souls.get(region.substring(4));
                }
                if (total != null) {
                    solutions = List.of(total + " Fairy Souls");
                    return;
                }
            }
        }

        solutions = ANSWERS.getOrDefault(trimmed, Collections.emptyList());
    }

    /** 从网站同步各区域的仙女魂总数；失败或还没拿到数据时下次用到会再试。 */
    private static void ensureFairySouls(boolean notifyOnFailure) {
        if (fairySouls != null) return;
        if (notifyOnFailure) notifyFailure = true;
        if (fairySoulsLoading) return;
        fairySoulsLoading = true;
        CompletableFuture.runAsync(() -> {
            boolean success = false;
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(FAIRY_SOULS_URL))
                        .timeout(Duration.ofSeconds(5)).GET().build();
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    ToolList.getInstance().log.warn("仙女魂数据同步失败：HTTP {}", response.statusCode());
                    return;
                }
                String body = response.body();
                // 网站上的文件带 UTF-8 BOM，先去掉再解析
                if (!body.isEmpty() && body.charAt(0) == '\uFEFF') body = body.substring(1);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                Map<String, Integer> totals = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        try {
                            totals.put(entry.getKey(), entry.getValue().getAsInt());
                        } catch (NumberFormatException ignored) {
                            // 非数字字段跳过
                        }
                    }
                }
                fairySouls = totals;
                success = true;
                ToolList.getInstance().log.info("仙女魂数据已同步，共 {} 个区域", totals.size());
            } catch (Exception e) {
                ToolList.getInstance().log.warn("仙女魂数据同步失败", e);
            } finally {
                fairySoulsLoading = false;
                if (success) {
                    notifyFailure = false;
                } else if (notifyFailure) {
                    notifyFailure = false;
                    ToolList.printChatMessage(Component.literal(
                            "§a[小沙雕] §c仙女魂数据拉取失败, 详情查看日志, 遇到关于仙女魂的问题可能需要你输入/quests自行解决"));
                }
            }
        });
    }

    /** Skyblock 历法：1 年 = 5 天 4 小时（真实时间），纪元 2019-06-11 00:35 UTC。 */
    private static int skyblockYear() {
        return (int) ((System.currentTimeMillis() - 1560275700000L) / 446_400_000L) + 1;
    }

    @Override
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
        BlockPos choice = switch (currentSolution) {
            case "ⓐ" -> CHOICE_A;
            case "ⓑ" -> CHOICE_B;
            case "ⓒ" -> CHOICE_C;
            default -> null;
        };
        if (choice == null) return;

        RenderUtils.WorldRender fill = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.NO_THROUGH_WALLS_FILL);
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        for (Direction direction : DIRECTIONS) {
            BlockPos pos = room.relativeToActual(choice.relative(direction));
            RenderUtils.renderESP(fill, pos, 0f, 1f, 0f, 0.5f, true, true);
            RenderUtils.renderESP(line, pos, 0f, 1f, 0f, 1f, false, true);
        }
        // 再用 tracer 直接指到答案方块
        RenderUtils.renderTrace(line, room.relativeToActual(choice), 0f, 1f, 0f, 1f);
        fill.finishDraw();
        line.finishDraw();
    }

    @Override
    public void reset() {
        // 离开房间不清答案：Quiz 允许在房间外继续答题，回来还要继续高亮
        checkedThisRoom = false;
    }

    /** 换局/换层时才真正清空状态。 */
    public static void resetRunState() {
        currentSolution = "";
        solutions = Collections.emptyList();
    }
}
