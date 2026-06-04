package pers.XiaoShadiao.skydiao.utils.blivesensitiveword;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Detector implements IDetectorAccessor {

    public static final Detector instance = new Detector();

    public Set<String> illegalWords = new HashSet<>();
    public Set<String> illegalWordsEnglish = new HashSet<>();
    public Set<String> illegalWordsChinese = new HashSet<>();

    public List<char[]> illegalWordsCharArray = new ArrayList<>();
    public List<char[]> illegalWordsEnglishCharArray = new ArrayList<>();
    public List<char[]> illegalWordsChineseCharArray = new ArrayList<>();

    public void loadWords(InputStream inputStream) {
        try {
            illegalWords.clear();
            illegalWordsEnglish.clear();
            illegalWordsChinese.clear();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "GBK"));
            String s;
            while((s = br.readLine()) != null) {
                illegalWords.add(new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8).replace(" ", "").toLowerCase());
            }
            illegalWords.removeIf(s2 -> !s2.matches("(.*)[\\u4e00-\\u9fa5](.*)") && s2.trim().length() <= 4 || s2.trim().length() <= 1 || s2.contains("器") || s2.contains("联") || s2.contains("点击") || s2.contains("代理") || s2.contains("破坏"));
            illegalWords.add("习");
            illegalWords.add("xjp");
            illegalWords.add("翠");
            illegalWords.add("熊");
            illegalWords.add("8964");
            illegalWords.add("6498");
            illegalWords.forEach(s3 -> (s3.matches("(.*)[\\u4e00-\\u9fa5](.*)") ? illegalWordsChinese : illegalWordsEnglish).add(s3));

            Iterator<String> it = illegalWordsEnglish.iterator();
            while(it.hasNext()) {
                String next = it.next();
                if(next.contains(".")) {
                    it.remove();
                    illegalWords.remove(next);
                }
            }

            illegalWords.stream().sorted(Comparator.comparingInt(String::length).reversed()).forEach(s2 -> illegalWordsCharArray.add(s2.toCharArray()));
            illegalWordsChinese.stream().sorted(Comparator.comparingInt(String::length).reversed()).forEach(s2 -> illegalWordsChineseCharArray.add(s2.toCharArray()));
            illegalWordsEnglish.stream().sorted(Comparator.comparingInt(String::length).reversed()).forEach(s2 -> illegalWordsEnglishCharArray.add(s2.toCharArray()));
        } catch(Exception e) {

        }
    }

    private final Set<Character>[] registerSameChar = new Set[] {
            new HashSet<>(Arrays.asList('i', 'l', '1')),
            new HashSet<>(Arrays.asList('i', 'l', '1')),
            new HashSet<>(Arrays.asList('s', '5')),
            new HashSet<>(Arrays.asList('o', '0')),
            new HashSet<>(Arrays.asList('z', '2')),
            new HashSet<>(Arrays.asList('e', '3')),
            new HashSet<>(Arrays.asList('b', '8')),
            new HashSet<>(Arrays.asList('b', '6')),
            new HashSet<>(Arrays.asList('q', '9')),
            new HashSet<>(Arrays.asList('g', '9')),
            new HashSet<>(Arrays.asList('g', '6')),
            new HashSet<>(Arrays.asList('0', '〇', '０', '⓪', '⓿', '⁰', '₀', '↉', '⓪', '⒪')),
            new HashSet<>(Arrays.asList('1', '１', '①', '⑴', '⒈', '➀', '➊', '❶', '➀', '¹', '₁')),
            new HashSet<>(Arrays.asList('一', '壹')),
            new HashSet<>(Arrays.asList('2', '２', '②', '⑵', '⒉', '➁', '➋', '❷', '➁', '²', '₂')),
            new HashSet<>(Arrays.asList('二', '贰')),
            new HashSet<>(Arrays.asList('3', '３', '③', '⑶', '⒊', '➂', '➌', '❸', '➂', '³', '₃')),
            new HashSet<>(Arrays.asList('三', '叁')),
            new HashSet<>(Arrays.asList('4', '４', '④', '⑷', '⒋', '➃', '➍', '❹', '➃', '⁴', '₄')),
            new HashSet<>(Arrays.asList('四', '肆')),
            new HashSet<>(Arrays.asList('5', '５', '⑤', '⑸', '⒌', '➄', '➎', '❺', '➄', '⁵', '₅')),
            new HashSet<>(Arrays.asList('五', '伍')),
            new HashSet<>(Arrays.asList('6', '６', '⑥', '⑹', '⒍', '➅', '➏', '❻', '➅', '⁶', '₆')),
            new HashSet<>(Arrays.asList('六', '陆')),
            new HashSet<>(Arrays.asList('7', '７', '⑦', '⑺', '⒎', '➆', '➐', '❼', '➆', '⁷', '₇')),
            new HashSet<>(Arrays.asList('七', '柒')),
            new HashSet<>(Arrays.asList('8', '８', '⑧', '⑻', '⒏', '➇', '➑', '❽', '➇', '⁸', '₈')),
            new HashSet<>(Arrays.asList('八', '捌')),
            new HashSet<>(Arrays.asList('9', '９', '⑨', '⑼', '⒐', '➈', '➒', '❾', '➈', '⁹', '₉')),
            new HashSet<>(Arrays.asList('九', '玖')),
            new HashSet<>(Arrays.asList('a', 'ａ', 'ⓐ', 'Ａ')),
            new HashSet<>(Arrays.asList('b', 'ｂ', 'ⓑ', 'Ｂ')),
            new HashSet<>(Arrays.asList('c', 'ｃ', 'ⓒ', '©')),
            new HashSet<>(Arrays.asList('d', 'ｄ', 'ⓓ', 'Ｄ')),
            new HashSet<>(Arrays.asList('e', 'ｅ', 'ⓔ', 'Ｅ')),
            new HashSet<>(Arrays.asList('f', 'ｆ', 'ⓕ', 'Ｆ')),
            new HashSet<>(Arrays.asList('g', 'ｇ', 'ⓖ', 'Ｇ')),
            new HashSet<>(Arrays.asList('h', 'ｈ', 'ⓗ', 'Ｈ')),
            new HashSet<>(Arrays.asList('i', 'ｉ', 'ⓘ', 'Ｉ', 'ℹ')),
            new HashSet<>(Arrays.asList('j', 'ｊ', 'ⓙ', 'Ｊ')),
            new HashSet<>(Arrays.asList('k', 'ｋ', 'ⓚ', 'Ｋ')),
            new HashSet<>(Arrays.asList('l', 'ｌ', 'ⓛ', 'Ｌ')),
            new HashSet<>(Arrays.asList('m', 'ｍ', 'ⓜ', 'Ｍ')),
            new HashSet<>(Arrays.asList('n', 'ｎ', 'ⓝ', 'Ｎ', '№')),
            new HashSet<>(Arrays.asList('o', 'ｏ', 'ⓞ', 'Ｏ', '⓪', '⓿', '⒪', 'º', '°')),
            new HashSet<>(Arrays.asList('p', 'ｐ', 'ⓟ', 'Ｐ', '¶', '℗')),
            new HashSet<>(Arrays.asList('q', 'ｑ', 'ⓠ', 'Ｑ')),
            new HashSet<>(Arrays.asList('r', 'ｒ', 'ⓡ', 'Ｒ', '®')),
            new HashSet<>(Arrays.asList('s', 'ｓ', 'ⓢ', 'Ｓ', '$')),
            new HashSet<>(Arrays.asList('t', 'ｔ', 'ⓣ', 'Ｔ', '™')),
            new HashSet<>(Arrays.asList('u', 'ｕ', 'ⓤ', 'Ｕ', '∪', '∩')),
            new HashSet<>(Arrays.asList('v', 'ｖ', 'ⓥ', 'Ｖ', '✓')),
            new HashSet<>(Arrays.asList('w', 'ｗ', 'ⓦ', 'Ｗ', 'ω')),
            new HashSet<>(Arrays.asList('x', 'ｘ', 'ⓧ', 'Ｘ', '×', '✕', '✖')),
            new HashSet<>(Arrays.asList('y', 'ｙ', 'ⓨ', 'Ｙ', '¥')),
            new HashSet<>(Arrays.asList('z', 'ｚ', 'ⓩ', 'Ｚ'))
    };

    private final Set<String> illegalWord1 = new HashSet<>(Arrays.asList("刁  羽  习  夕  兮  戏  汐  西  吸  希  系  饩  昔  析  矽  穸  细  郄  洗  茜  郗  唏  奚  席  息  栖  浠  牺  玺  徙  悉  惜  欷  淅  烯  硒  菥  袭  觋  铣  阋  喜  晰  犀  稀  粞  翕  腊  舄  舾  葸  隙  媳  溪  皙  禊  裼  锡  僖  屣  熄  熙  蓰  蜥  嘻  嬉  膝  樨  歙  熹  禧  羲  螅  褶  隰  檄  蟋  蹊  醯  曦  鼷  晞  窸  潟  娭  囍  習  戲  係  餼  細  郤  蓆  棲  犧  璽  襲  覡  銑  鬩  臘  潟  谿  錫  譆".split(" {2}")));
    private final Set<String> illegalWord2 = new HashSet<>(Arrays.asList("巾  仅  今  钅  尽  劲  妗  近  进  卺  金  津  矜  荩  衿  晋  浸  烬  紧  赆  堇  筋  缙  禁  谨  锦  靳  廑  馑  槿  瑾  觐  噤  襟  浕  琎  僅  釒  盡  勁  進  巹  藎  晉  燼  緊  贐  縉  謹  錦  饉  覲  濜  璡  井  阱  刭  劲  京  净  弪  径  泾  经  肼  茎  迳  胫  颈  荆  痉  竞  婧  惊  旌  竟  菁  敬  景  晶  腈  靓  睛  粳  靖  儆  兢  境  獍  精  静  憬  镜  鲸  警  璟  剄  勁  淨  弳  徑  涇  經  莖  逕  脛  頸  荊  痙  競  驚  靚  靜  鏡  鯨".split(" {2}")));
    private final Set<String> illegalWord3 = new HashSet<>(Arrays.asList("乎  于  干  千  牝  拚  贫  品  姘  拼  娉  嫔  榀  聘  频  颦  貧  嬪  頻  顰  冯  平  乒  评  凭  坪  苹  俜  屏  枰  娉  瓶  萍  鲆  馮  評  憑  蘋  鮃".split(" {2}")));

    private final Set<String> totalIllegalWord = new HashSet<>();
    {
        totalIllegalWord.addAll(illegalWord1);
        totalIllegalWord.addAll(illegalWord2);
        totalIllegalWord.addAll(illegalWord3);
    }
    // 习洗戏吸喜系惜嬉袭悉嘻刁
    private final Pattern[] patterns = new Pattern[] {
            // Pattern.compile("([习洗刁])([^一-龥]*)?([一-龥]([^一-龥]*)?[一-龥]?)?"),
            // Pattern.compile("[xX][，；。：！？….,:;_=　/!@#$%^&*()]*([iIl])(?!([aoieun]|(an)))"),
            Pattern.compile("8_*9_*6_*4"),
            Pattern.compile("[" + String.join("", totalIllegalWord) + "]{3}")
    };

    private final Cache<@NotNull String, @NotNull IllegalWordResult> illegalWordCache = CacheBuilder.newBuilder()
            .maximumSize(5000)  // 最大5000个条目
            .expireAfterAccess(5, TimeUnit.MINUTES)  // 5分钟未访问则过期
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .build();

    public IllegalWordResult scanIllegalWords(String stringIn, boolean scanEnglish, boolean scanChinese) {
        try {
            return illegalWordCache.get(stringIn, () -> scanIllegalWords1(stringIn, scanEnglish, scanChinese));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

    }
    private final ForkJoinPool sensitiveWordTasks = new ForkJoinPool(Math.max(1, (int) (Runtime.getRuntime().availableProcessors() / 1.5)));

    private IllegalWordResult scanIllegalWords1(String stringIn, boolean scanEnglish, boolean scanChinese) {

        IllegalWordResult instance = new IllegalWordResult();

        a:{
            if(scanChinese) {
                for (int i = 0, len = stringIn.length(); i < len; i++) {
                    char ch = stringIn.charAt(i);
                    if (ch >= '一' && ch <= '龥') {
                        break a;
                    }
                }
                scanChinese = false;
            }
        }

        // 初始化参数
        instance.C = scanChinese;
        instance.E = scanEnglish;
        instance.replaceCount.set(0);
        instance.beforeTransfer = stringIn;
        instance.isIllegal = false;
        instance.words.clear();

        // 标记敏感词位置的数组（AtomicBooleanArray替代原boolean[]）
        AtomicIntegerArray shouldBeep = new AtomicIntegerArray(stringIn.length());
        // 预处理：记录大写字母和特殊字符
        boolean[] upperCasesIndex = new boolean[stringIn.length()];
        float[] bypassChars = new float[stringIn.length()];
        for (int i = 0; i < stringIn.length(); i++) {
            char ch = stringIn.charAt(i);
            upperCasesIndex[i] = Character.isUpperCase(ch);
            bypassChars[i] = " ，；。：！？….,:;_=　\\/!@#$%^&*()".indexOf(ch) != -1 ? 1 :
                    "0123456789".indexOf(ch) != -1 ? 0.5f : 0;
        }
        int abcd = 0;
        for (Pattern pattern : patterns) {
            abcd++;
            
            Matcher matcher = pattern.matcher(stringIn);
            int searchStart = 0;

            while (searchStart < stringIn.length()) {
                if (matcher.find(searchStart)) {  // 从指定位置开始搜索
                    int start = matcher.start();
                    int end = matcher.end();

                    for (int i = start; i < end; i++) {
                        shouldBeep.set(i, 1);
                    }
                    instance.isIllegal = true;
                    instance.replaceCount.incrementAndGet();
                    instance.words.add("*** MATCHER " + abcd + " ***");

                    // 关键：下次从 start+1 开始
                    searchStart = start + 1;
                } else {
                    break;  // 没有更多匹配
                }
            }
        }

        String lowerCaseString = stringIn.toLowerCase();

        // 获取待扫描的词库
        List<char[]> list = Collections.emptyList();
        if (scanEnglish) list = illegalWordsEnglishCharArray;
        if (scanChinese) list = illegalWordsChineseCharArray;
        if (scanEnglish && scanChinese) list = illegalWordsCharArray;

        List<Future<?>> futures = null;
        // 并行流处理词库（无锁！）
        if (ToolList.getInstance().stringHasContext(lowerCaseString)) {

            int batchSize = Math.max(100, list.size() / (sensitiveWordTasks.getParallelism() * 4));

            futures = new ArrayList<>(list.size() / batchSize + 1);

            for (int p = 0; p < list.size(); p += batchSize) {
                final List<char[]> batch = list.subList(p, Math.min(p + batchSize, list.size()));

                futures.add(sensitiveWordTasks.submit(() -> {
                    for (char[] cs : batch) {
                        if (cs.length <= lowerCaseString.length()) {  // 提前过滤
                            int dontBypassCheck = cs.length <= 5 ? 1 : cs.length == 6 ? 2 : cs.length == 7 ? 3 : -1;
                            int lastReplaceIndex = 0;
                            int tries = 0;

                            bb: while (true) {
                                int index, lastIndex = 0, startIndex = 0, endIndex = 0, totalSkipIndex = 0, totalSkipIndexLimit;
                                boolean found = true;
                                float totalBypassChatCount = 0;

                                if(lastReplaceIndex >= lowerCaseString.length()) found = false;
                                a: for (int i = lastReplaceIndex; i < lowerCaseString.length(); i += 2) {
                                    if (tries++ > 50) break bb;
                                    lastIndex = startIndex = endIndex = totalSkipIndex = 0;
                                    boolean start = true;
                                    totalSkipIndexLimit = cs.length;
                                    found = true;
                                    lastIndex = i;

                                    for (char c : cs) {
                                        if (dontBypassCheck != -1 && c >= 0x4e00 && c <= 0x9fa5) dontBypassCheck = -1;
                                        float bypassChatCount = 0;

                                        // 查找字符位置（兼容相似字符）
                                        f: {
                                            index = -1;
                                            for (Set<Character> characters : registerSameChar) {
                                                if (characters.contains(boxChar(c))) {
                                                    for (Character a : characters) {
                                                        int temp = lowerCaseString.indexOf(a, lastIndex);
                                                        if (temp != -1 && (index == -1 || temp < index)) index = temp;
                                                    }
//                                                    int finalLastIndex = lastIndex;
//                                                    index = characters.stream().
//                                                            mapToInt(a -> lowerCaseString.indexOf(a, finalLastIndex))
//                                                            .filter(ppp -> ppp != -1)
//                                                            .min()
//                                                            .orElse(-1);

                                                    if(index != -1) break f;
                                                }
                                            }
                                            index = lowerCaseString.indexOf(c, lastIndex);
                                        }

                                        totalSkipIndex += index - lastIndex;
                                        if (index != -1) {
                                            for (int m = lastIndex; m < index; m++) {
                                                if (bypassChars[m] != 0) bypassChatCount += bypassChars[m];
                                            }
                                            totalBypassChatCount += bypassChatCount;
                                        }
                                        if (start) {
                                            i = startIndex = lastIndex = index;
                                            lastReplaceIndex = i + 1;
                                            totalSkipIndex = 0;
                                            start = false;
                                            if (index == -1) {
                                                found = false;
                                                break bb;
                                            }
                                        }

                                        if (index != -1 && (index - lastIndex < (dontBypassCheck == -1 ? 3 : dontBypassCheck) + bypassChatCount || lastIndex == 0)
                                                && totalSkipIndex <= totalSkipIndexLimit + totalBypassChatCount) {
                                            lastIndex = index + 1;
                                        } else {
                                            found = false;
                                            break;
                                        }
                                    }
                                    if (found) break a;
                                }

                                if (found) {
                                    endIndex = lastIndex - 1;
                                    // 无锁修改共享状态！
                                    instance.isIllegal = true; // volatile写
                                    for (int i = startIndex; i <= endIndex; i++) {
                                        shouldBeep.set(i, 1); // 原子操作
                                    }
                                    instance.words.add(new String(cs)); // 线程安全集合
                                    instance.replaceCount.incrementAndGet(); // 原子递增

                                    if (ToolList.getInstance().isXiaoShadiao()) {
                                        ToolList.getInstance().log.info("Scanned illegal word: {} :|: {} :|: {} -> {}",
                                                lowerCaseString, new String(cs), startIndex, endIndex);
                                    }
                                    continue;
                                }
                                break;
                            }
                        }
                    }
                }));

            }
        }

        if(futures != null) for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        // 生成最终过滤后的字符串
        StringBuilder sb = new StringBuilder(lowerCaseString.length());
        for (int i = 0; i < lowerCaseString.length(); i++) {
            char ch = lowerCaseString.charAt(i);
            ch = upperCasesIndex[i] ? Character.toUpperCase(ch) : Character.toLowerCase(ch);
            if (shouldBeep.get(i) == 1) {
                ch = (ch >= 0x4e00 && ch <= 0x9fa5) ? (i % 2 == 0 ? '原' : '神') : '*';
            }
            sb.append(ch);
        }
        instance.transfered = sb.toString();

        if (instance.isIllegal && (ToolList.getInstance().isXiaoShadiao())) {
            ToolList.getInstance().log.info("Final transfer result: {} :|: {} :|: words: {} :|: placecount: {}",
                    lowerCaseString, instance.transfered, instance.words, instance.replaceCount);
        }

        return instance;
    }

    private final Character[] charCaches = new Character[0x10000];

    {
        for (int i = 0; i < charCaches.length; i++) {
            charCaches[i] = (char) i;
        }
    }

    private Character boxChar(char c) {
        return charCaches[c];
    }
}
