package pers.XiaoShadiao.skydiao.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HypixelRewardClaimer {

    private static HypixelRewardClaimer hrc;
    public final String url;
    
    public List<RewardData> rewards = new ArrayList<>();

    public static final Function<JsonElement, String> funcS = JsonElement::getAsString;
    public static final Function<JsonElement, Integer> funcI = JsonElement::getAsInt;
    
    public Thread claimThread, connectThread;
    
    private List<String> cookies = new ArrayList<>();
    
    public boolean hasData, claimed;
    private Integer target;
    private Integer highestLevel;
    private Integer highestLevelTarget;

    private static boolean useXSDServerFlag = true;
    private final boolean useXSDServer = (useXSDServerFlag = !useXSDServerFlag);
    
    public HypixelRewardClaimer(String url) {
        this.url = url;
        if(!url.contains("hypixel.net/claim-reward/")) throw new RuntimeException("不是有效的Hypixel Reward链接");
        createThread();
    }
    
    private void createThread() {
        hasData = true;
        connectThread = new Thread(this::doConnect1);
        connectThread.setName("Hyp每日奖励 - 链接");
        claimThread = new Thread(this::doClaim1);
        claimThread.setName("Hyp每日奖励 - 领取");
    }
    
    public void doClaim() {
        claimThread.start();
    }

    public void doConnect() {
        connectThread.start();
    }

    public void setTargetReward(int i) {
        if(!(i >= 0 && i <= 2)) throw new RuntimeException("非法的参数: " + i + ", 取值范围应在0~2");
        target = i;
    }

    public void setHighestTargetReward() {
        target = highestLevelTarget;
    }
    
    public static HypixelRewardClaimer getCurrent() { return hrc; }
    
    public static HypixelRewardClaimer get(String url) {
        return hrc = new HypixelRewardClaimer(url);
    }
    
    public void doConnect1() {

        if(ToolList.mc.isSameThread()) throw new RuntimeException("不要在主线程上调用");
        
        try {
            // "取出InputStream的ByteArray并转成String";
            Entry<URLConnection, URLConnection> entry = new Entry<URLConnection, URLConnection>() {

                private URLConnection value;

                @Override
                public URLConnection getKey() {
                    throw new RuntimeException();
                }

                @Override
                public URLConnection getValue() {
                    return value;
                }

                @Override
                public URLConnection setValue(URLConnection value) {
                    this.value = value;
                    return value;
                }
            };
            String result;
            JsonObject jo;
            String auth = null;
            if(!useXSDServer)  {

                try(InputStream is = ToolList.getInstance().makeReqToURL(url, false, entry::setValue, null)) {
                    result = (new String(is.readAllBytes(), StandardCharsets.UTF_8));
                }
                jo = JsonParser.parseString(ToolList.getInstance().getMidOfText(result, "window.appData = '", "'")).getAsJsonObject();
            } else {
                Matcher m = Pattern.compile("[0-9a-fA-F]+$").matcher(url);
                if(m.find()) {
                    try(InputStream is = ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club/xsdwk/claimhypdailyreward?action=get&rewardcode=" + m.group(), false, entry::setValue, null)) {
                        result = (new String(is.readAllBytes(), StandardCharsets.UTF_8));
                    }
                } else throw new RuntimeException("不该发生的错误发生了");
                jo = JsonParser.parseString(result).getAsJsonObject();
                auth = jo.get("auth").getAsString();
                System.out.println(auth);
                jo = jo.getAsJsonObject("rawdata");
            }

            System.out.println(jo);
            Map<String, List<String>> map = entry.getValue().getHeaderFields();
            for(Entry<String, List<String>> e : map.entrySet()) {
                if(e.getKey() != null && e.getKey().toLowerCase().equals("set-cookie")) {
                    cookies.addAll(e.getValue());
                }
            }
            // System.out.println(cookiesToString(cookies));
            
            if(jo.has("error")) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c链接已失效"));
                return;
            }

            int ii = 0;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e吓到你了吗?"));
            for(JsonElement je : jo.get("rewards").getAsJsonArray()) {

                RewardData rd = new RewardData();
                rd.auth = auth;
                // tip = "取出result文本左边和右边文本";
                rd.securityToken = ToolList.getInstance().getMidOfText(result, "window.securityToken = \"", "\"");
                rd.activeAd = jo.get("activeAd").getAsInt();
                rd.id = jo.get("id").getAsString();
                
                JsonObject jo1 = je.getAsJsonObject();
                rd.amount = Optional.ofNullable(jo1.get("amount")).map(funcI).orElse(0);
                
                rd.gameType = Optional.ofNullable(jo1.get("gameType")).map(funcS).orElse(null);
                rd.rarity = Optional.ofNullable(jo1.get("rarity")).map(funcS).orElse(null);
                rd.reward = Optional.ofNullable(jo1.get("reward")).map(funcS).orElse(null);
                // String rarity = jo1.get("rarity").getAsString();
                // String reward = jo1.get("reward").getAsString();
                int level = 1;
                if("COMMON".equals(rd.rarity)) {
                    rd.rarity = "§f" + rd.rarity;
                    level = 1;
                } else if("UNCOMMON".equals(rd.rarity)) {
                    rd.rarity = "§a" + rd.rarity;
                    level = 2;
                } else if("RARE".equals(rd.rarity)) {
                    rd.rarity = "§9" + rd.rarity;
                    level = 3;
                } else if("EPIC".equals(rd.rarity)) {
                    rd.rarity = "§d" + rd.rarity;
                    level = 4;
                } else if("LEGENDARY".equals(rd.rarity)) {
                    rd.rarity = "§6" + rd.rarity;
                    level = 5;
                }
                if(highestLevel == null) {
                    highestLevelTarget = ii;
                    highestLevel = level;
                } else {
                    // highestLevel = Math.max(highestLevel, level);
                    if(level > highestLevel) {
                        highestLevel = level;
                        highestLevelTarget = ii;
                    } 
                }

                StringBuilder sb = new StringBuilder();
                sb.append(rd.rarity);
                sb.append(" ");
                if(rd.gameType != null) {
                    sb.append(rd.gameType);
                    sb.append(" ");
                }
                sb.append(rd.reward);
                if(rd.amount != 0)  {
                    sb.append(" x");
                    sb.append(rd.amount);
                }

                String sb2 = "§a[小沙雕] §f - " + sb;
                rd.cacheMessage = sb.toString();
                
                rewards.add(rd);
                
                // tip = "/hhsc claimreward 指令会调用doClaim方法";
                ToolList.printChatMessage(Component.literal(sb2)
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent.RunCommand("/hhsc claimreward " + ii))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§6点击领取awa"))
                                )
                        )
                );

                ii++;
            }
            System.out.println("最高等级项: " + highestLevelTarget);
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e** 点击上面的其中一个领取 **"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e(Src: " + (useXSDServer ? "XSDServer" : "Hyp") + ")"));
            
        } catch(Throwable e) {
            createThread();
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c获取数据失败: " + e));
        }
        
    }

    public void doClaim1() {
        
        if(ToolList.mc.isSameThread()) throw new RuntimeException("不要在主线程上调用");
        if(target == null) throw new RuntimeException("未设置领取目标");
        
        boolean flag = ToolList.getInstance().random.nextBoolean();
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §b" + (flag ? "小礼物..." : "有趣吗?")));
        
        RewardData rd = rewards.get(target);
        
        try {
            if(!useXSDServer) {
                // HttpsURLConnection huc = (HttpsURLConnection) new URL("https://rewards.hypixel.net/claim-reward/claim?option=" + target + "&id=" + rd.id + "&activeAd=" + rd.activeAd + "&_csrf=" + rd.securityToken + "&watchedFallback=false&skipped=0").openConnection();
                // huc = (HttpsURLConnection) new URL("https://rewards.hypixel.net/claim-reward/claim?option=1&id=48672fc9&activeAd=1&_csrf=4SmpLeAp-AuvjjqefaL09RsSiqVRLZwwkkW4&watchedFallback=false&skipped=0").openConnection();
                System.out.println(cookiesToString(cookies));
//            huc.addRequestProperty("Cookie", cookiesToString(cookies));
//            huc.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0");
//            huc.setRequestMethod("POST");
//
//            huc.setConnectTimeout(10000);
//            huc.setReadTimeout(10000);
//
//            huc.connect();

                // tip = "取出InputStream的ByteArray并转成String";
                try(InputStream is = ToolList.getInstance().makeReqToURL("https://rewards.hypixel.net/claim-reward/claim?option=" + target + "&id=" + rd.id + "&activeAd=" + rd.activeAd + "&_csrf=" + rd.securityToken + "&watchedFallback=true&skipped=0", false, uc -> {
                    uc.addRequestProperty("Cookie", cookiesToString(cookies));
                    try {
                        ((HttpsURLConnection) uc).setRequestMethod("POST");
                    } catch (Exception ignored) {
                    }
                }, null)) {
                    System.out.println(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                }
            } else {
                try(InputStream is = ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club/xsdwk/claimhypdailyreward?action=claim&rewardcode=" + rd.id + "&claimindex=" + target + "&auth=" + rd.auth);) {
                    System.out.println(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            claimed = true;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §6" + (flag ? "大惊喜!" : "祝你愉快!")));

        } catch(Throwable e) {
            createThread();
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c获取数据失败: " + e));
        }
    }
    
    private static String cookiesToString(Collection<String> c) {
        
        Iterator<String> it = c.iterator();
        if (! it.hasNext())
            return "";

        StringBuilder sb = new StringBuilder();
        // sb.append('[');
        for (;;) {
            String e = it.next();
            sb.append(e);
            if (! it.hasNext())
                return sb.toString();
            sb.append(';').append(' ');
        }
    }
    
    public class RewardData {

        public String gameType;
        public String rarity;
        public String reward;
        public String id;
        public String securityToken;
        public int activeAd;
        public int amount;
        
        public String cacheMessage;

        public String auth;
        
    }
    
}
