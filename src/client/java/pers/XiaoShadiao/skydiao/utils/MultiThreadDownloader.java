package pers.XiaoShadiao.skydiao.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * 通用多线程分段下载器。
 * <p>
 * 通过 HTTP Range 把文件切成若干段并行下载；线程数取 CPU 核数，
 * 每个分段大小为 max(20 / 线程数, 1) MB。
 * 既支持随机写入的文件目标，也支持只能顺序写入的流目标。
 */
public final class MultiThreadDownloader {

    public static final Logger log = LogManager.getLogger();

    private static final int BUFFER_SIZE = 256 * 1024;
    private static final int MAX_RETRY = 12;            // 无进展时的最大重试次数（有进展会自动重置）
    private static final int MAX_BACKOFF_SECONDS = 30;  // 单次重试最长等待秒数
    private static final int MAX_REDIRECTS = 10;
    private static final long TOO_MANY_REQUESTS_WAIT_MILLIS = 10_000L; // 429 时的等待时间

    private MultiThreadDownloader() {
    }

    /** 下载到文件，支持断点式覆盖写入与分段并行写。 */
    public static Throwable download(String url, File dest) {
        return download(url, dest, null);
    }

    /**
     * 下载到文件，并在下载完成后校验 MD5。
     *
     * @param md5 期望的 MD5，为 null 或空时跳过校验
     * @return 成功返回 null；请求失败返回对应的异常；MD5 不匹配返回包含 "MD5不匹配" 的异常
     */
    public static Throwable download(String url, File dest, String md5) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(dest, "dest");

        if(verify(dest, md5)) return null;
        try {
            if(!dest.exists()) {
                File parentFile = dest.getParentFile();
                if(parentFile != null && !parentFile.exists()) parentFile.mkdirs();
                dest.createNewFile();
            }
            Probe probe = probe(url);
            if (!probe.rangeSupported()) {
                // 服务器不支持 Range，只能单连接完整下载
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                    downloadWhole(url, out);
                }
            } else {
                try (RandomAccessFile raf = new RandomAccessFile(dest, "rw");
                     FileChannel channel = raf.getChannel()) {
                    raf.setLength(probe.total()); // 预分配文件，稀疏写入
                    Throwable failure = downloadSegments(url, probe.total(), channel);
                    if (failure != null) {
                        return fail(url, failure);
                    }
                    channel.force(true);
                }
            }
            if (md5 != null && !md5.isBlank()) {
                if (!verify(dest, md5)) {
                    return new IOException("MD5不匹配");
                }
            }
            return null;
        } catch (Exception e) {
            return fail(url, e);
        }
    }

    /** 下载到输出流。分段乱序并行下载，再按分段顺序依次写出。 */
    public static Throwable download(String url, OutputStream dest) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(dest, "dest");

        try {
            Probe probe = probe(url);
            if (!probe.rangeSupported()) {
                downloadWhole(url, dest);
                return null;
            }
            Throwable failure = downloadSegmentsInOrder(url, probe.total(), dest);
            return failure == null ? null : fail(url, failure);
        } catch (Exception e) {
            return fail(url, e);
        }
    }

    /** 分段并行写入文件（位置写由 FileChannel 保证线程安全）。 */
    private static Throwable downloadSegments(String url, long total, FileChannel channel) {
        int threads = threadCount();
        long chunk = chunkSizeBytes(threads);
        int count = segmentCount(total, chunk);
        if (count == 0) {
            return null;
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, count));
        List<Future<?>> futures = new ArrayList<>(count);
        try {
            for (int i = 0; i < count; i++) {
                long start = i * chunk;
                long end = Math.min(start + chunk, total) - 1;
                futures.add(pool.submit(() -> {
                    byte[] data = fetchSegment(url, start, end);
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    long writePos = start;
                    while (buffer.hasRemaining()) {
                        writePos += channel.write(buffer, writePos);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    return e.getCause() != null ? e.getCause() : e;
                }
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 分段并行下载后按顺序写入输出流。
     * 只维持一个窗口内的分段在内存中，避免某个慢分段导致整份文件堆积。
     */
    private static Throwable downloadSegmentsInOrder(String url, long total, OutputStream dest) throws IOException {
        int threads = threadCount();
        long chunk = chunkSizeBytes(threads);
        int count = segmentCount(total, chunk);
        if (count == 0) {
            dest.flush();
            return null;
        }

        int poolSize = Math.min(threads, count);
        int window = poolSize * 2;
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        CompletionService<SegmentResult> completion = new ExecutorCompletionService<>(pool);
        Map<Integer, byte[]> pending = new HashMap<>();
        int submitted = 0;
        int written = 0;
        try {
            while (submitted < count && submitted < window) {
                completion.submit(segmentTask(url, submitted, chunk, total));
                submitted++;
            }
            while (written < count) {
                SegmentResult result;
                try {
                    result = completion.take().get();
                } catch (ExecutionException e) {
                    return e.getCause() != null ? e.getCause() : e;
                }
                pending.put(result.index(), result.data());
                while (pending.containsKey(written)) {
                    dest.write(pending.remove(written));
                    written++;
                    if (submitted < count && submitted < written + window) {
                        completion.submit(segmentTask(url, submitted, chunk, total));
                        submitted++;
                    }
                }
            }
            dest.flush();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } finally {
            pool.shutdownNow();
        }
    }

    private static java.util.concurrent.Callable<SegmentResult> segmentTask(String url, int index, long chunk, long total) {
        long start = index * chunk;
        long end = Math.min(start + chunk, total) - 1;
        return () -> new SegmentResult(index, fetchSegment(url, start, end));
    }

    /** 下载单个分段，失败后从已下载位置继续重试。 */
    private static byte[] fetchSegment(String url, long start, long end) throws IOException {
        long length = end - start + 1;
        if (length <= 0 || length > Integer.MAX_VALUE - 8) {
            throw new IOException("分段大小非法: " + start + "-" + end);
        }
        byte[] data = new byte[(int) length];
        int attempt = 0;
        long pos = start;
        while (true) {
            long before = pos;
            try {
                pos = fetchRange(url, start, pos, end, data);
                return data;
            } catch (IOException e) {
                if (e instanceof RateLimitedException) {
                    // 429 属于服务端限流，不计入重试次数
                    sleep(TOO_MANY_REQUESTS_WAIT_MILLIS);
                    continue;
                }
                if (pos > before) {
                    attempt = 0; // 只要还有进展就重置计数，避免慢速下载被误判为失败
                }
                if (++attempt > MAX_RETRY) {
                    throw new IOException("分段 " + start + "-" + end + " 下载失败", e);
                }
                long waitSeconds = Math.min(MAX_BACKOFF_SECONDS, 2L * attempt);
                sleep(waitSeconds * 1000L);
            }
        }
    }

    /** 拉取 [pos, end] 区间并写入 data，返回新的已下载位置。 */
    private static long fetchRange(String url, long start, long pos, long end, byte[] data) throws IOException {
        HttpURLConnection conn = open(url, "bytes=" + pos + "-" + end);
        try {
            int code = conn.getResponseCode();
            if (code == 429) {
                throw new RateLimitedException("HTTP 429 Too Many Requests");
            }
            if (code != 206) {
                throw new IOException("期望 206，实际 " + code + " " + conn.getResponseMessage());
            }
            String contentRange = conn.getHeaderField("Content-Range");
            if (contentRange != null && !contentRange.startsWith("bytes " + pos + "-")) {
                throw new IOException("Content-Range 不匹配: " + contentRange);
            }

            long current = pos;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream in = conn.getInputStream()) {
                int len;
                while ((len = in.read(buffer)) != -1) {
                    if (current + len > end + 1) {
                        throw new IOException("返回数据超出分段范围");
                    }
                    System.arraycopy(buffer, 0, data, (int) (current - start), len);
                    current += len;
                }
            }
            if (current != end + 1) {
                if (current == pos) {
                    throw new IOException("服务器返回了空的 206 包体");
                }
                throw new IOException("分段未下载完整: " + current + "/" + (end + 1));
            }
            return current;
        } finally {
            conn.disconnect();
        }
    }

    /** 用 Range: bytes=0-0 探测总大小，同时确认服务器是否支持分段。 */
    private static Probe probe(String url) throws IOException {
        while (true) {
            HttpURLConnection conn = open(url, "bytes=0-0");
            try {
                int code = conn.getResponseCode();
                if (code == 429) {
                    sleep(TOO_MANY_REQUESTS_WAIT_MILLIS);
                    continue;
                }
                if (code == 206) {
                    String contentRange = conn.getHeaderField("Content-Range"); // bytes 0-0/187651303
                    if (contentRange == null) {
                        throw new IOException("缺少 Content-Range 响应头");
                    }
                    int slash = contentRange.lastIndexOf('/');
                    if (slash < 0 || slash == contentRange.length() - 1) {
                        throw new IOException("无法解析 Content-Range: " + contentRange);
                    }
                    return new Probe(Long.parseLong(contentRange.substring(slash + 1)), true);
                }
                if (code == 200) {
                    // 不支持 Range，按 Content-Length 判断大小（未知时为 -1）
                    return new Probe(conn.getContentLengthLong(), false);
                }
                throw new IOException("下载探测失败: HTTP " + code + " " + conn.getResponseMessage());
            } finally {
                conn.disconnect();
            }
        }
    }

    /** 单连接完整下载，用于服务器不支持 Range 的情况。 */
    private static void downloadWhole(String url, OutputStream out) throws IOException {
        while (true) {
            HttpURLConnection conn = open(url, null);
            try {
                int code = conn.getResponseCode();
                if (code == 429) {
                    sleep(TOO_MANY_REQUESTS_WAIT_MILLIS);
                    continue;
                }
                if (code != 200 && code != 206) {
                    throw new IOException("下载失败: HTTP " + code + " " + conn.getResponseMessage());
                }
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
                out.flush();
                return;
            } finally {
                conn.disconnect();
            }
        }
    }

    /** 建立连接，并手动跟随重定向（保证每一跳都带上相同的 Range 等请求头）。 */
    private static HttpURLConnection open(String url, String range) throws IOException {
        String current = url;
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(current).toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Connection", "close"); // 不复用连接，避免拿到已失效的连接
            conn.setRequestProperty("Accept-Encoding", "identity"); // 防止压缩导致长度/分段错乱
            if (range != null) {
                conn.setRequestProperty("Range", range);
            }

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IOException("HTTP " + code + " 缺少 Location");
                }
                current = URI.create(current).resolve(location).toString();
                continue;
            }
            return conn;
        }
        throw new IOException("重定向次数过多");
    }

    private static int threadCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /** 每个分段大小：max(20 / 线程数, 1) MB。 */
    private static long chunkSizeBytes(int threads) {
        double megabytes = Math.max(20.0 / threads, 1.0);
        return (long) (megabytes * 1024 * 1024);
    }

    private static int segmentCount(long total, long chunk) {
        return (int) ((total + chunk - 1) / chunk);
    }

    private static void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载被中断", e);
        }
    }

    /** 记录并返回下载失败原因。 */
    private static Throwable fail(String url, Throwable failure) {
        log.error("下载失败: {}", url, failure);
        return failure;
    }

    private record Probe(long total, boolean rangeSupported) {
    }

    private record SegmentResult(int index, byte[] data) {
    }

    /** 服务端限流（HTTP 429），该错误不计入分段重试次数。 */
    private static final class RateLimitedException extends IOException {
        RateLimitedException(String message) {
            super(message);
        }
    }

    private static String getMD5(File file) {
        try {
            return getMD5(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMD5(InputStream inputStream) {
        try (inputStream) {
            try {
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                byte[] buffer = new byte[8192]; // 8KB缓冲区
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    md5.update(buffer, 0, bytesRead); // 分块更新摘要
                }

                byte[] digest = md5.digest();
                return byteToHex(digest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String byteToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(32); // 预分配长度
        for (byte b : bytes) {
            int i = b & 0xFF;
            if (i < 16) {
                hexString.append('0');
            }
            hexString.append(Integer.toHexString(i));
        }
        return hexString.toString();
    }

    private static boolean verify(File file, String md5) {
        try {
            return getMD5(file).equalsIgnoreCase(md5);
        } catch (Throwable t) {
            return false;
        }
    }

}
