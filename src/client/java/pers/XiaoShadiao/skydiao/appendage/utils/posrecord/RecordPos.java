package pers.XiaoShadiao.skydiao.appendage.utils.posrecord;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static pers.XiaoShadiao.skydiao.SkyDiaoModClient.MOD_ID;

public class RecordPos {
    public static PositionList plist = null;
    public static final Path SAVE_DIRECTORY = FabricLoader.getInstance().getGameDir().resolve("config/Ipositions");
    public static Gson gson = new GsonBuilder().registerTypeAdapter(Vec3.class, (JsonSerializer<Vec3>) (vec, type, context) -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("x", vec.x);
                obj.addProperty("y", vec.y);
                obj.addProperty("z", vec.z);
                return obj;
            })
            .setPrettyPrinting()
            .create();

    public static Logger logger = LoggerFactory.getLogger(RecordPos.class);

    public static void start(String name){
        plist = new PositionList(name, new ArrayList<>());
    }

    public static void add(Player player){
        plist.positions().add(to2(player.position()));
    }

    public static Vec3 to2(Vec3 vec){
        return new Vec3(to2_2(vec.x()), to2_2(vec.y()), to2_2(vec.z()));
    }

    public static double to2_2(double d){
        return Math.round(d*100)/100d;
    }

    public static void save(){
        if(!SAVE_DIRECTORY.toFile().exists()) {
            try {
                java.nio.file.Files.createDirectories(SAVE_DIRECTORY);
            } catch (Exception e) {
                LoggerFactory.getLogger(MOD_ID).error("Failed to create save directory", e);
            }
        }


        Path filePath = SAVE_DIRECTORY.resolve(plist.name() + ".json");
        try (FileWriter writer = new FileWriter(filePath.toFile(), StandardCharsets.UTF_8)) {
            writer.write(gson.toJson(plist));

        } catch (IOException e) {

        }
        plist = null;
    }

    public static PositionList getLocal(String filename){
        Path filePath = SAVE_DIRECTORY.resolve(filename);
        File file = filePath.toFile();
        if(!file.exists()){return null;}
        try {
            String content = FileUtils.readFileToString(file, "UTF-8");

            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            String name = obj.get("name").getAsString();
            ArrayList<Vec3> positions = new ArrayList<>();
            JsonArray obj2 = obj.getAsJsonArray("positions");
            for(JsonElement jsonElement : obj2){
                double x = Double.parseDouble(jsonElement.getAsJsonObject().get("x").getAsString());
                double y = Double.parseDouble(jsonElement.getAsJsonObject().get("y").getAsString());
                double z = Double.parseDouble(jsonElement.getAsJsonObject().get("z").getAsString());
                positions.add(new Vec3(x, y, z));
            }
            return new PositionList(name,positions);

        } catch (IOException e) {
            logger.error("Failed to read file", e);
            return null;
        }


    }

    public static List<String> getPListFileNames(){
        File directory = RecordPos.SAVE_DIRECTORY.toFile();
        File[] files = directory.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return null;
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        ArrayList<String> slist = new ArrayList<>();
        List.of(files).forEach(f->slist.add(f.getName()));
        return slist;

    }



}
