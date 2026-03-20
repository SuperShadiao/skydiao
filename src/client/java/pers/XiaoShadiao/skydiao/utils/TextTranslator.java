package pers.XiaoShadiao.skydiao.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.*;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TextTranslator {

    private final String text;
    private boolean isSuccess = false;
    
    private static final String salt;
    private static final String AppId;
    private static final String AppKey;

    static {
        salt = String.valueOf("114514");
        AppId = String.valueOf("20210625000872230");
        AppKey = String.valueOf("IJPcNYl7g_djq3zMa2WE");
    }
    private boolean f;
    public TextTranslator(String text) {
        this.text = text;
    }

    public void execute() {
        if(f) throw new RuntimeException("你tm是不是翻译过了?"); else f = true;
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e小沙雕正在翻译ing: " + text));
        execute(true);
    }

    private void execute(boolean useE) {

        Thread t = new Thread(() -> {
            String result0 = "";
            String aaaa = "";

            boolean isTargetZh = D(text);
            try(InputStream is = ToolList.getInstance().makeReqToURL("https://fanyi-api.baidu.com/api/trans/vip/translate?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&salt=" + salt + "&appid=" + AppId + "&sign=" + ToolList.getInstance().getMD5(AppId + text + salt + AppKey) + "&from=auto&to=" + (isTargetZh ? "zh" : "en"))) {

                JsonElement je = JsonParser.parseString(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                ToolList.getInstance().log.info(je);
                JsonObject jo = je.getAsJsonObject();

                if(jo.get("error_code") == null) {
                    JsonArray ja = jo.getAsJsonArray("trans_result");
                    if(ja.size() != 1) {
                        result0 = "§a[小沙雕] §d翻译成功: ";
                        for(JsonElement a : ja) {
                            result0 += "\n§a[小沙雕] §e - §f" + a.getAsJsonObject().get("dst").getAsString() + " ";
                            aaaa += a.getAsJsonObject().get("dst").getAsString() + " ";
                        }
                    } else result0 = "§a[小沙雕] §d翻译成功: §f" + (aaaa = ja.get(0).getAsJsonObject().get("dst").getAsString());
                    isSuccess = true;
                } else {
                    result0 = "§a[小沙雕] §c翻译失败: §f" + (aaaa = jo.get("error_msg").getAsString());
                    result0 = result0 + (result0.endsWith("Please recharge") ? " (sb百度又要圈我$了, 火速进群" + SkyDiaoModClient.CONST_QQGROUP_MAIN + "提醒小沙雕给sb百度打钱)" : "");
                }

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                result0 = "§a[小沙雕] §c翻译失败: §f" + (aaaa = "翻译时出错了");
            }
            if(!isSuccess && useE) {
                execute(false);
                return;
            }
            MutableComponent component = Component.literal(result0);
            component.append(Component.literal(" §e[§n点击复制§e]").withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent.CopyToClipboard(aaaa))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§6§k|§6 还犹豫啥啊,快点击啊awa §k|")))
            ));
            ToolList.printChatMessage(component);
            if(isTargetZh && aaaa.equals(text)) {
                boolean replaced = false;
                char[] charArray = text.toCharArray();
                for (int i = 0; i < charArray.length; i++) {
                    if(charArray[i] >= '\u4E00' && charArray[i] <= '\u9FA5') {
                        charArray[i] = 'x';
                        replaced = true;
                    }
                }
                if(replaced) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §e翻译到中文时结果与原文相同, 尝试替换掉所有中文字符重试..."));
                    new TextTranslator(new String(charArray)).execute();
                }
            }
        });
        t.setName("沙雕的生草机");
        t.start();

    }

	private boolean D(String s) {
		int a = 0,b = 0;
		for(char c : s.toCharArray()) {
			// if(String.valueOf(c).matches("[\u4E00-\u9FA5]")) a++; else b++;
            if(c >= '\u4E00' && c <= '\u9FA5') a++; else b++;
		}
		return a < b;
	}
}
