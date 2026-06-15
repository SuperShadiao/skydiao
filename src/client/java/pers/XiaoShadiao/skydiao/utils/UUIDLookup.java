package pers.XiaoShadiao.skydiao.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

public class UUIDLookup {

    public static CompletableFuture<String> getNameByUUID(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try(InputStream is = ToolList.getInstance().makeReqToURL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)) {
                JsonObject jo = JsonParser.parseReader(new JsonReader(new InputStreamReader(is))).getAsJsonObject();
                return jo.get("name").getAsString();
            } catch (Throwable e) {
                return e;
            }
        }).thenCompose(callback -> {
            if(callback instanceof Throwable t) {
                return CompletableFuture.failedFuture(new RuntimeException("Lookup uuid failed", t));
            } else if(callback instanceof String str) {
                return CompletableFuture.completedFuture(str);
            } else {
                return CompletableFuture.failedFuture(new AssertionError());
            }
        });
    }

}
