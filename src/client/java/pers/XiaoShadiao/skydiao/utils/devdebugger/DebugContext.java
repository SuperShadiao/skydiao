package pers.XiaoShadiao.skydiao.utils.devdebugger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次探针请求的上下文，只提供最常用的入口，避免探针里写重复样板。
 */
public final class DebugContext {

    private final Path requestDir;

    DebugContext(Path requestDir) {
        this.requestDir = requestDir;
    }

    public Minecraft mc() {
        return Minecraft.getInstance();
    }

    public ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    public LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    /** 本次请求的落盘目录，形如 {@code run/debug-probes/p<纳秒>}。 */
    public Path requestDir() {
        return requestDir;
    }

    /** 把数据写进本次请求目录，返回绝对路径；用于导出 PNG、原始快照等。 */
    public Path write(String fileName, byte[] data) throws IOException {
        Path file = requestDir.resolve(fileName);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, data);
        return file;
    }
}
