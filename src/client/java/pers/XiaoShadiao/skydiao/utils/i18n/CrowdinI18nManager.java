package pers.XiaoShadiao.skydiao.utils.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CrowdinI18nManager {

    private static File i18nCacheDir = null;

    public enum LangCode {
        chinese("zh", "中文 | Chinese"),
        english("en", "英语 | English"),
        german("de",  "德语 | German");

        public static final Int2ObjectOpenHashMap<LangCode> langCodeHashMap = new Int2ObjectOpenHashMap<>();

        static {
            for(LangCode langCode : values()) {
                langCodeHashMap.put(langCode.hashCode(), langCode);
            }
        }

        public final String code;
        public final String displayName;

        LangCode(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }
    }

    public static final List<LangCode> supportedLangCodes = List.of(LangCode.values());

    public static ToolList.ThreadedTask<?> initI18nFromConfig() {
        return initI18n(LangCode.values()[ConfigManager.language.getValue()]);
    }

    public static ToolList.ThreadedTask<?> initI18n(LangCode langCode) {
        return ToolList.addThreadedTask(() -> {
            JsonObject cache = kvMapCache.get(langCode);
            if(cache != null) {
                kvMap = cache;
                return;
            }
            JsonObject temp = null;
            File cacheFile = getCacheFile(langCode);
            if(langCode == LangCode.chinese) {
                temp = kvMap = fetchCN();
            } else {
                try {
                    temp = kvMap = fetch(langCode);
                } catch (Exception e) {
                    e.printStackTrace();
                    if(cacheFile.exists()) {
                        try {
                            kvMap = JsonParser.parseString(FileUtils.readFileToString(cacheFile, StandardCharsets.UTF_8)).getAsJsonObject();
                        } catch (Exception e1) {
                            e1.printStackTrace();
                            kvMap = fetchCN();
                        }
                    } else {
                        kvMap = fetchCN();
                    }
                }
            }
            // System.out.println(kvMap);
            if(temp != null) {
                kvMapCache.put(langCode, temp);
                try {
                    FileUtils.writeStringToFile(cacheFile, temp.toString(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, null);
    }

    private static JsonObject fetchCN() {
        if(cnKvMap != null) {
            return cnKvMap;
        }
        try(InputStream is = CrowdinI18nManager.class.getClassLoader().getResourceAsStream("assets/skydiao/I18n/defaultLang.json")) {
            String data = new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
            JsonObject jsonObject = JsonParser.parseString(data).getAsJsonObject();
            JsonObject result = new JsonObject();
            flattenJsonObject(jsonObject, "", result);
            return (cnKvMap = result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonObject fetch(LangCode langCode) {
        String json = null;
        try(InputStream is = ToolList.getInstance().makeReqToURL("https://api.crowdin.com/api/v2/projects/856714/translations/builds/files/18", true, (uc0) -> {
            HttpURLConnection uc = (HttpURLConnection) uc0;
            try {
                uc.addRequestProperty("Authorization", "Bearer " + "06789f5f23bcfcd3573b94a3f7218537215f071f2d280c2f526e11d597f4616e468eeab86bfc6e4d");
                uc.addRequestProperty("Accept", "application/json");
                uc.addRequestProperty("Content-Type", "application/json");
                uc.setRequestMethod("POST");
                uc.setDoOutput(true);
                JsonObject jo = new JsonObject();
                jo.addProperty("targetLanguageId", langCode.code);
                if (!ToolList.getInstance().isXiaoShadiao()) jo.addProperty("exportApprovedOnly", true);
                uc.getOutputStream().write(jo.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, null)) {
            String data = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            try(InputStream is2 = ToolList.getInstance().makeReqToURL(JsonParser.parseString(data).getAsJsonObject().get("data").getAsJsonObject().get("url").getAsString())) {
                json = new String(is2.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject jo = JsonParser.parseString(json).getAsJsonObject();
            JsonObject result = new JsonObject();
            flattenJsonObject(jo, "", result);
            return result;
        } catch (Exception e) {
            if(json == null) throw new RuntimeException(e);
            else throw new RuntimeException("Bad Content: " + json, e);
        }
    }

    public static LangCode fromSystemLanguage() {
        String systemLang = Locale.getDefault().getLanguage().toLowerCase();

        return switch (systemLang) {
            case "en" -> LangCode.english;
            case "de" -> LangCode.german;
            default -> LangCode.chinese;
        };
    }

    private static JsonObject kvMap, cnKvMap;
    private static final Map<LangCode, JsonObject> kvMapCache = new HashMap<>();
    private static final LangCode currentLang = LangCode.chinese;

    private static void flattenJsonObject(JsonObject jsonObj, String parentKey, JsonObject result) {
        for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet()) {
            String newKey = parentKey.isEmpty() ? entry.getKey() : parentKey + "." + entry.getKey();

            if (entry.getValue().isJsonObject()) {
                flattenJsonObject(entry.getValue().getAsJsonObject(), newKey, result);
            } else if (entry.getValue().isJsonPrimitive()) {
                result.add(newKey, entry.getValue());
            } else if (entry.getValue().isJsonArray()) {
                // 如果是数组，可以按需处理
                entry.getValue().getAsJsonArray().forEach((v) -> {
                    if(v.isJsonObject()) {
                        flattenJsonObject(v.getAsJsonObject(), newKey, result);
                    } else {
                        result.add(newKey + "." + v.getAsString(), v);
                    }
                });
            }
        }
    }

    private static String findKeyInMap(JsonObject map, String key) {
        if(!map.has(key)) {
            if(cnKvMap.has(key)) return cnKvMap.get(key).getAsString();
            return key;
        } else {
            return map.get(key).getAsString();
        }
    }

    private static String findKeyInMap(JsonObject map, String key, String ...format) {
        if(!map.has(key)) {
            if(cnKvMap.has(key)) return String.format(cnKvMap.get(key).getAsString(), (Object[]) format);
            return key;
        } else {
            String string = map.get(key).getAsString();
            try {
                return String.format(string, (Object[]) format);
            } catch (Exception e) {
                return "Format Error: " + string;
            }
        }
    }

    public static LangCode getCurrentLang() {
        return currentLang;
    }

    private static File getCacheFile(LangCode langCode) {
        if(i18nCacheDir == null) {
            i18nCacheDir = new File(ConfigManager.config_folder, "i18n");
            if(!i18nCacheDir.exists()) i18nCacheDir.mkdirs();
        }
        return new File(i18nCacheDir, langCode.code + ".json");
    }

    public static String translate(String key) {
        return findKeyInMap(kvMap, key);
    }

    public static String translate(String key, String ...format) {
        return findKeyInMap(kvMap, key, format);
    }

    public static String translateWithLang(LangCode langCode, String key) {
        JsonObject target = kvMapCache.get(langCode);
        if(target == null) {
            return translate(key);
        }
        return findKeyInMap(target, key);
    }

    public static String translateWithLang(LangCode langCode, String key, String ...format) {
        JsonObject target = kvMapCache.get(langCode);
        if(target == null) {
            return translate(key, format);
        }
        return findKeyInMap(target, key, format);
    }

}
