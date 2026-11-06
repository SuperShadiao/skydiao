package pers.XiaoShadiao.skydiao.mixin.client;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import net.minecraft.util.FileUtil;
import net.minecraft.util.HttpUtil;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.MultiThreadDownloader;
import pers.XiaoShadiao.skydiao.utils.MultiThreadDownloader.DownloadInfo;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.OptionalLong;

/**
 * 把原版 {@link HttpUtil#downloadFile} 的实际传输交给多线程下载器：
 * 丢弃原连接上的请求头、代理等配置，只把 URL 与目标文件交给 {@link MultiThreadDownloader}。
 * 缓存文件位置、哈希校验、监听器回调与临时文件清理等行为保持与原版一致。
 */
@Mixin(HttpUtil.class)
public class MixinHttpUtilDownload {

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    private static Path cachedFilePath(Path path, HashCode hash) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean checkExistingFile(Path path, HashFunction hashFunction, HashCode expectedHash) throws IOException {
        throw new AssertionError();
    }

    @Shadow
    private static HashCode hashFile(Path path, HashFunction hashFunction) throws IOException {
        throw new AssertionError();
    }

    @Shadow
    private static void updateModificationTime(Path path) {
        throw new AssertionError();
    }

    @Inject(method = "downloadFile", at = @At("HEAD"), cancellable = true)
    private static void skydiao$downloadFile(Path path, URL url, Map<String, String> headers,
                                             HashFunction hashFunction, HashCode expectedHash,
                                             int maxSize, Proxy proxy,
                                             HttpUtil.DownloadProgressListener listener,
                                             CallbackInfoReturnable<Path> cir) throws IOException {

        if(ConfigManager.highSpeedDownload.getValue()) {
            ToolList.getInstance().log.info("使用Skydiao高速下崽器下载文件: " + url + " 到 " + path);

            listener.requestStart();

            Path cachePath = null;
            if (expectedHash != null) {
                cachePath = cachedFilePath(path, expectedHash);
                boolean exists;
                try {
                    exists = checkExistingFile(cachePath, hashFunction, expectedHash);
                } catch (IOException e) {
                    LOGGER.warn("Failed to check cached file {}", cachePath, e);
                    exists = false;
                }
                if (exists) {
                    LOGGER.info("Returning cached file since actual hash matches requested");
                    listener.requestFinished(true);
                    updateModificationTime(cachePath);
                    cir.setReturnValue(cachePath);
                    return;
                }
                LOGGER.warn("Existing file {} not found or had mismatched hash", cachePath);
                try {
                    Files.deleteIfExists(cachePath);
                } catch (IOException e) {
                    listener.requestFinished(false);
                    throw new UncheckedIOException("Failed to remove existing file " + cachePath, e);
                }
            }

            FileUtil.createDirectoriesSafe(path);

            Path tempPath = null;
            try {
                Path target;
                if (cachePath != null) {
                    target = cachePath;
                } else {
                    tempPath = Files.createTempFile(path, "download", ".tmp");
                    target = tempPath;
                }

                // 只把 URL、目标文件与原版请求头交给多线程下载器，代理等其余连接参数丢弃
                // （部分服务端（如 Hypixel）会按 X-Minecraft-* 请求头返回不同版本的资源包）
                DownloadInfo info = MultiThreadDownloader.download(url.toString(), target.toFile(), null, headers).addToTitle();

                while (!info.getFuture().isDone() && info.getTotalByteSize() <= 0) {
                    Thread.sleep(10);
                }
                long total = info.getTotalByteSize();
                listener.downloadStart(total > 0 ? OptionalLong.of(total) : OptionalLong.empty());

                long reported = 0;
                while (!info.getFuture().isDone()) {
                    long downloaded = info.getDownloadedByteSize();
                    if (downloaded > reported) {
                        listener.downloadedBytes(downloaded);
                        reported = downloaded;
                    }
                    Thread.sleep(10);
                }
                long downloaded = info.getDownloadedByteSize();
                if (downloaded > reported) {
                    listener.downloadedBytes(downloaded);
                }

                Throwable failure = info.getFuture().join();
                if (failure != null) {
                    throw new IOException("Failed to download file " + url, failure);
                }
                long actualSize = Math.max(total, downloaded);
                if (actualSize > maxSize) {
                    Files.deleteIfExists(target);
                    throw new IOException("Filesize is bigger than maximum allowed (file is "
                            + actualSize + ", limit is " + maxSize + ")");
                }

                Path result;
                if (cachePath != null) {
                    HashCode actual = hashFile(cachePath, hashFunction);
                    if (!actual.equals(expectedHash)) {
                        throw new IOException("Hash of downloaded file (" + actual
                                + ") did not match requested (" + expectedHash + ")");
                    }
                    result = cachePath;
                } else {
                    HashCode downloadedHash = hashFile(tempPath, hashFunction);
                    result = cachedFilePath(path, downloadedHash);
                    if (!checkExistingFile(result, hashFunction, downloadedHash)) {
                        Files.move(tempPath, result, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        updateModificationTime(result);
                    }
                }

                listener.requestFinished(true);
                cir.setReturnValue(result);
            } catch (Throwable t) {
                listener.requestFinished(false);
                throw new IllegalStateException("Failed to download file " + url, t);
            } finally {
                if (tempPath != null) {
                    try {
                        Files.deleteIfExists(tempPath);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}
