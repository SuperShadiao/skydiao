package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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

    private static final BlockPos pos = new BlockPos(3, 79, 3);
    private static final BlockPos privateIslandPos = new BlockPos(7, 96, 7);
    private static final Map<String, PosRecord> posMap = Map.of(
            "hub", new PosRecord(pos, RenderUtils.SideDirection.N, RenderUtils.TopMode.SIDE),
            "dynamic",  new PosRecord(privateIslandPos, RenderUtils.SideDirection.S, RenderUtils.TopMode.TOP)
    );
    record PosRecord(BlockPos pos, RenderUtils.SideDirection sideDirection, RenderUtils.TopMode topMode) {}

    private int pictureIndex = 1;

    private static final int currentAvailableGifVersion = 1;

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
                try(URLFetchProcess process = new URLFetchProcess(ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club/gifs.zip"))) {
                    titleChanger.downloadProcess.addProcess(process);
                    File temp = File.createTempFile("gifs", ".zip");
                    FileUtils.copyInputStreamToFile(process, temp);
                    if(gifFileSystem != null) {
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

    private void onCollectSubmits(LevelRenderContext context) {
        String mode = StatusManager.get().getMode();
        if(mode == null) return;
        PosRecord posRecord = posMap.get(mode);
        if(posRecord == null) return;

        if(!false && ToolList.getInstance().isDevEnvironment()) pictureIndex = 36;

        Gif hiddenSomething = switch(isInitedFromRemote ? 0 : 1) {
            case 0 -> Gif.create(
                    Identifier.fromNamespaceAndPath("skydiao", "textures/hiddensomething/" + pictureIndex + ".gif"),
                            () -> Files.newInputStream(gifFileSystem.getPath("/" + pictureIndex + ".gif"))
                    );
            default -> Gif.create(defaultGif);
        };

        RenderUtils.renderTextureOnBlockSide(context, posRecord.pos(), hiddenSomething.updateAndGetFrame().resourceId(), posRecord.sideDirection(), posRecord.topMode());
    }

}
