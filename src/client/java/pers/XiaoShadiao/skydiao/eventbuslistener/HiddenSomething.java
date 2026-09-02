package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.URLFetchProcess;
import pers.XiaoShadiao.skydiao.utils.renderutils.Gif;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Map;

public class HiddenSomething extends AbstractListener {

    private int pictureIndex = 1;

    private int currentAvailableGifVersion = 1;

    private static final File gifs = new File(ConfigManager.config_folder, "gifs.zip");

    private static final Identifier defaultGif = Identifier.fromNamespaceAndPath("skydiao", "textures/hiddensomething/default.gif");

    private boolean isInitedFromRemote = false;
    private int remoteGifs = 0;
    private int remoteVersion = 0;
    private FileSystem gifFileSystem;

    @Override
    public String getListenerName() {
        return "HiddenSomething";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::onCollectSubmits);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);

        loadFromRemote();
    }

    private void loadFromRemote() {
        if (!isInitedFromRemote) {
            if(!gifs.exists()) {
                downloadRemoteGifs();
            } else {
                try {
                    gifFileSystem = FileSystems.newFileSystem(gifs.toPath());

                    try(InputStream inputStream = Files.newInputStream(gifFileSystem.getPath("/data.json"))) {
                        String json = new String(inputStream.readAllBytes());
                        JsonObject jo = JsonParser.parseString(json).getAsJsonObject();
                        int v = jo.get("v").getAsInt();
                        remoteVersion = v;
                        remoteGifs = jo.get("gif").getAsInt();
                        isInitedFromRemote = true;
                        if(v < currentAvailableGifVersion) {
                            downloadRemoteGifs();
                        }
                    }
                } catch (IOException e) {
                    downloadRemoteGifs();
                }
            }
        }
    }

    private void downloadRemoteGifs() {
        ToolList.addThreadedTask(() -> {
            int tries = 0;
            while (tries < 3) {
                try(URLFetchProcess process = new URLFetchProcess(ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club/gifs.zip")).addToTitle()) {
                    File temp = File.createTempFile("gifs", ".zip");
                    FileUtils.copyInputStreamToFile(process, temp);
                    if(gifFileSystem != null) {
                        isInitedFromRemote = false;
                        gifFileSystem.close();
                        gifs.delete();
                    }
                    FileUtils.moveFile(temp, gifs);
                    loadFromRemote();
                    break;
                } catch (Exception e) {
                    logger.catching(e);
                    tries++;
                }
            }
            return null;
        });
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        if(remoteGifs > 0) pictureIndex = ToolList.getInstance().random.nextInt(remoteGifs) + 1;
    }

    public void setIndex(int index) {
        pictureIndex = index;
    }

    record PosRecord(Vec3 pos, RenderUtils.SideDirection sideDirection, RenderUtils.TopMode topMode) {}

    private static final Map<String, PosRecord> posMap = Map.ofEntries(
            Map.entry("hub", new PosRecord(new Vec3(3, 79, 3), RenderUtils.SideDirection.N, RenderUtils.TopMode.SIDE)),
            Map.entry("dynamic",  new PosRecord(new Vec3(7, 96, 7), RenderUtils.SideDirection.S, RenderUtils.TopMode.TOP)),
            Map.entry("dungeon_hub", new PosRecord(new Vec3(-58, 140, 0), RenderUtils.SideDirection.W, RenderUtils.TopMode.SIDE)),
            Map.entry("mining_3", new PosRecord(new Vec3(129, 197, 198), RenderUtils.SideDirection.N, RenderUtils.TopMode.SIDE)),
            Map.entry("crystal_hollows", new PosRecord(new Vec3(472, 114, 513), RenderUtils.SideDirection.W, RenderUtils.TopMode.SIDE)),
            Map.entry("foraging_2", new PosRecord(new Vec3(-665, 70, 59), RenderUtils.SideDirection.N, RenderUtils.TopMode.SIDE)),
            Map.entry("foraging_3", new PosRecord(new Vec3(-729, 112.5, 145), RenderUtils.SideDirection.S, RenderUtils.TopMode.BOTTOM)),
            Map.entry("combat_1", new PosRecord(new Vec3(-276, 121, -182), RenderUtils.SideDirection.E, RenderUtils.TopMode.SIDE)),
            Map.entry("combat_3", new PosRecord(new Vec3(-583, 27, -322), RenderUtils.SideDirection.S, RenderUtils.TopMode.BOTTOM)),
            Map.entry("fishing_1", new PosRecord(new Vec3(53, 78, 11), RenderUtils.SideDirection.E, RenderUtils.TopMode.SIDE)),
            Map.entry("lotus_atoll", new PosRecord(new Vec3(88, 79, 27), RenderUtils.SideDirection.N, RenderUtils.TopMode.SIDE))
    );

    private void onCollectSubmits(LevelRenderContext context) {
        String mode = StatusManager.get().getMode();
        if(mode == null) return;
        PosRecord posRecord = (ToolList.getInstance().isDevEnvironment() ?
                Map.ofEntries(
                        Map.entry("hub", new PosRecord(new Vec3(3, 79, 3), RenderUtils.SideDirection.N, RenderUtils.TopMode.SIDE)),
                        Map.entry("dynamic",  new PosRecord(new Vec3(7, 96, 7), RenderUtils.SideDirection.S, RenderUtils.TopMode.TOP)),
                        Map.entry("dungeon_hub", new PosRecord(new Vec3(-58, 140, 0), RenderUtils.SideDirection.W, RenderUtils.TopMode.SIDE)),
                        Map.entry("mining_3", new PosRecord(new Vec3(129, 197, 198), RenderUtils.SideDirection.N, RenderUtils.TopMode.SIDE)),
                        Map.entry("crystal_hollows", new PosRecord(new Vec3(472, 114, 513), RenderUtils.SideDirection.W, RenderUtils.TopMode.SIDE)),
                        Map.entry("foraging_2", new PosRecord(new Vec3(-665, 70, 59), RenderUtils.SideDirection.N, RenderUtils.TopMode.SIDE)),
                        Map.entry("foraging_3", new PosRecord(new Vec3(-729, 112.5, 145), RenderUtils.SideDirection.S, RenderUtils.TopMode.BOTTOM)),
                        Map.entry("combat_1", new PosRecord(new Vec3(-276, 121, -182), RenderUtils.SideDirection.E, RenderUtils.TopMode.SIDE)),
                        Map.entry("combat_3", new PosRecord(new Vec3(-583, 27, -322), RenderUtils.SideDirection.S, RenderUtils.TopMode.BOTTOM)),
                        Map.entry("fishing_1", new PosRecord(new Vec3(53, 78, 11), RenderUtils.SideDirection.E, RenderUtils.TopMode.SIDE)),
                        Map.entry("lotus_atoll", new PosRecord(new Vec3(88, 79, 27), RenderUtils.SideDirection.N, RenderUtils.TopMode.SIDE))
                )
        : posMap).get(mode);
        if(posRecord == null) return;

        if(false && ToolList.getInstance().isDevEnvironment()) pictureIndex = 38;

        Gif hiddenSomething = switch(isInitedFromRemote ? 0 : 1) {
            case 0 -> Gif.create(
                    Identifier.fromNamespaceAndPath("skydiao", "textures/hiddensomething/" + pictureIndex + ".gif"),
                            () -> Files.newInputStream(gifFileSystem.getPath("/" + pictureIndex + ".gif"))
                    );
            default -> Gif.create(defaultGif);
        };

        RenderUtils.renderTextureOnBlockSide(context, posRecord.pos(), hiddenSomething.updateAndGetFrame().resourceId(), posRecord.sideDirection(), posRecord.topMode());
    }

    public void setCurrentAvailableGifVersion(int version) {
        currentAvailableGifVersion = version;
    }

}
