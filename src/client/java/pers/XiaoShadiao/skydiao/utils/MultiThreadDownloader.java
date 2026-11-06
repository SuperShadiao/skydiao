package pers.XiaoShadiao.skydiao.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketException;
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
 * 每个分段最大 100 MB。分段较大时，空闲线程会继续拆分未领取的部分来帮助下载。
 * 既支持随机写入的文件目标，也支持只能顺序写入的流目标。
 */
public final class MultiThreadDownloader {

    public static final Logger log = LogManager.getLogger();
    /** 调试日志：记录工作线程与其负责的下载区间。 */
    private static final Logger DEBUG_LOG = LogManager.getLogger("Multi Thread Downloader");

    private static final int BUFFER_SIZE = 256 * 1024;
    private static final int MAX_RETRY = 12;            // 无进展时的最大重试次数（有进展会自动重置）
    private static final int MAX_BACKOFF_SECONDS = 30;  // 单次重试最长等待秒数
    private static final int MAX_STALE_RETRY = 5;       // 复用连接失效时的立即重连次数上限
    private static final int MAX_REDIRECTS = 10;
    private static final long TOO_MANY_REQUESTS_WAIT_MILLIS = 10_000L; // 429 时的等待时间
    private static final long MAX_CHUNK_SIZE = 100L * 1024 * 1024;     // 单个分段的最大大小
    private static final long STREAM_BUFFER_BUDGET = 256L * 1024 * 1024; // 流目标同时保留的分段缓冲总量
    private static final long PIECE_BYTES = 1024 * 1024;               // 单次领取的最大拉取长度
    private static final long MIN_STEAL_BYTES = 1024;                  // 帮助线程切分的最小粒度（1KB）

    /** 承载下载任务的后台执行器（守护线程，避免阻止进程退出）。 */
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "MultiThreadDownloader");
        thread.setDaemon(true);
        return thread;
    });

    private MultiThreadDownloader() {
    }

    /** 下载任务信息：完成状态与实时进度。 */
    public static final class DownloadInfo implements IDownloadProcess {
        private CompletableFuture<Throwable> future;
        private volatile long totalByteSize;
        private volatile long downloadedByteSize;

        /** 下载完成的结果：成功为 null，否则为失败原因。 */
        public CompletableFuture<Throwable> getFuture() {
            return future;
        }

        /** 文件总字节数；未知时为 0。 */
        public long getTotalByteSize() {
            return totalByteSize;
        }

        /** 已下载字节数。 */
        public long getDownloadedByteSize() {
            return downloadedByteSize;
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public boolean isMaxByteSizeUnknown() {
            return true;
        }

        private void setTotal(long total) {
            totalByteSize = Math.max(0, total);
        }

        private synchronized void addDownloaded(long bytes) {
            downloadedByteSize += bytes;
        }

        public void waitOrThrowThrowableIfFailed() throws Throwable {
            Throwable result = future.join();
            if (result != null) {
                throw result;
            }
        }

        public void waitOrThrowExceptionIfFailed() throws Exception {
            Throwable result = future.join();
            if(result instanceof Exception ex) {
                throw ex;
            } else if (result != null) {
                throw new RuntimeException(result);
            }
        }

        @Override
        public DownloadInfo addToTitle() {
            IDownloadProcess.super.addToTitle();
            return this;
        }
    }

    /** 下载到文件，支持断点式覆盖写入与分段并行写。 */
    public static DownloadInfo download(String url, File dest) {
        return download(url, dest, null, null);
    }

    /**
     * 下载到文件，并在下载完成后校验 MD5。下载在后台线程执行。
     *
     * @param md5 期望的 MD5，为 null 或空时跳过校验
     */
    public static DownloadInfo download(String url, File dest, String md5) {
        return download(url, dest, md5, null);
    }

    /**
     * 下载到文件，并在下载完成后校验 MD5，可携带额外的请求头。
     *
     * @param md5     期望的 MD5，为 null 或空时跳过校验
     * @param headers 附加请求头，为 null 时不附加
     */
    public static DownloadInfo download(String url, File dest, String md5, Map<String, String> headers) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(dest, "dest");

        DownloadInfo info = new DownloadInfo();
        info.future = CompletableFuture.supplyAsync(() -> {
            try {
                return downloadToFile(url, dest, md5, headers, info);
            } catch (Throwable t) {
                return fail(url, t);
            }
        }, DOWNLOAD_EXECUTOR);
        return info;
    }

    /** 下载到输出流。分段乱序并行下载，再按分段顺序依次写出。 */
    public static DownloadInfo download(String url, OutputStream dest) {
        return download(url, dest, null);
    }

    /** 下载到输出流，并携带额外的请求头。 */
    public static DownloadInfo download(String url, OutputStream dest, Map<String, String> headers) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(dest, "dest");

        DownloadInfo info = new DownloadInfo();
        info.future = CompletableFuture.supplyAsync(() -> {
            try {
                return downloadToStream(url, dest, headers, info);
            } catch (Throwable t) {
                return fail(url, t);
            }
        }, DOWNLOAD_EXECUTOR);
        return info;
    }

    private static Throwable downloadToFile(String url, File dest, String md5,
                                           Map<String, String> headers, DownloadInfo info) throws Exception {
        if (verify(dest, md5)) {
            info.setTotal(dest.length());
            info.addDownloaded(dest.length());
            return null;
        }
        if (!dest.exists()) {
            File parentFile = dest.getParentFile();
            if (parentFile != null && !parentFile.exists()) parentFile.mkdirs();
            dest.createNewFile();
        }
        Endpoint endpoint = new Endpoint(url);
        Probe probe = probe(endpoint, headers);
        info.setTotal(probe.total());
        if (!probe.rangeSupported()) {
            // 服务器不支持 Range，只能单连接完整下载
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                downloadWhole(endpoint, out, info, headers);
            }
        } else {
            try (RandomAccessFile raf = new RandomAccessFile(dest, "rw");
                 FileChannel channel = raf.getChannel()) {
                raf.setLength(probe.total()); // 预分配文件，稀疏写入
                Throwable failure = downloadSegments(endpoint, probe.total(), channel, info, headers);
                if (failure != null) {
                    return fail(url, failure);
                }
                channel.force(true);
            }
        }
        if (md5 != null && !md5.isBlank() && !verify(dest, md5)) {
            return new IOException("MD5不匹配");
        }
        return null;
    }

    private static Throwable downloadToStream(String url, OutputStream dest,
                                             Map<String, String> headers, DownloadInfo info) throws Exception {
        Endpoint endpoint = new Endpoint(url);
        Probe probe = probe(endpoint, headers);
        info.setTotal(probe.total());
        if (!probe.rangeSupported()) {
            downloadWhole(endpoint, dest, info, headers);
            return null;
        }
        Throwable failure = downloadSegmentsInOrder(endpoint, probe.total(), dest, info, headers);
        return failure == null ? null : fail(url, failure);
    }

    /** 文件目标：分段任务并行下载，直接按绝对位置写入文件。 */
    private static Throwable downloadSegments(Endpoint endpoint, long total, FileChannel channel,
                                             DownloadInfo info, Map<String, String> headers) {
        int threads = threadCount();
        long chunk = MAX_CHUNK_SIZE;
        int count = segmentCount(total, chunk);
        if (count == 0) {
            return null;
        }

        SegmentScheduler scheduler = new SegmentScheduler(endpoint, total, count, chunk, info, false, headers,
                (task, offset, data) -> {
                    long writePos = task.start + offset;
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    while (buffer.hasRemaining()) {
                        writePos += channel.write(buffer, writePos);
                    }
                });
        scheduler.openWindow(Integer.MAX_VALUE); // 文件目标：全部分段一次性入队
        // 始终按线程数创建工作线程：分段数少于线程数时，空闲线程会拆分剩余数据参与下载
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> runWorker(scheduler));
            }
            scheduler.waitAllDone();
            return scheduler.error();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } finally {
            scheduler.markFinished();
            pool.shutdownNow();
        }
    }

    /**
     * 流目标：窗口内的分段并行下载，完成后按顺序写出。
     * 只维持一个窗口内的分段在内存中，避免某个慢分段导致整份文件堆积。
     */
    private static Throwable downloadSegmentsInOrder(Endpoint endpoint, long total, OutputStream dest,
                                                     DownloadInfo info, Map<String, String> headers) throws IOException {
        int threads = threadCount();
        long chunk = MAX_CHUNK_SIZE;
        int count = segmentCount(total, chunk);
        if (count == 0) {
            dest.flush();
            return null;
        }

        // 流目标需要为每个分段保留完整缓冲，窗口大小受内存预算限制
        int window = (int) Math.max(2, Math.min((long) threads * 2, STREAM_BUFFER_BUDGET / chunk));
        SegmentScheduler scheduler = new SegmentScheduler(endpoint, total, count, chunk, info, true, headers,
                (task, offset, data) -> System.arraycopy(data, 0, task.buffer, (int) offset, data.length));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            scheduler.openWindow(window);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> runWorker(scheduler));
            }

            Map<Integer, SegmentTask> ready = new HashMap<>();
            int written = 0;
            while (written < count) {
                Throwable failure = scheduler.error();
                if (failure != null) {
                    return failure;
                }
                SegmentTask task = ready.remove(written);
                if (task == null) {
                    task = scheduler.pollCompleted(50);
                    if (task == null) {
                        continue;
                    }
                    if (task.index != written) {
                        ready.put(task.index, task);
                        continue;
                    }
                }
                dest.write(task.buffer);
                written++;
                scheduler.openWindow((long) written + window);
            }
            dest.flush();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } finally {
            scheduler.markFinished();
            pool.shutdownNow();
        }
    }

    /** 工作线程：不断领取任务；队列空了就去帮助其他分段，直到全部完成或出现失败。 */
    private static void runWorker(SegmentScheduler scheduler) {
        try {
            while (!scheduler.isFinished()) {
                SegmentScheduler.Job job = scheduler.nextJob();
                if (job == null) {
                    scheduler.awaitWork();
                    continue;
                }
                Throwable failure = scheduler.execute(job);
                scheduler.jobFinished(job, failure);
                if (failure != null) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 下载单个分段，失败后从已下载位置继续重试。 */
    private static byte[] fetchSegment(Endpoint endpoint, long start, long end, DownloadInfo info,
                                       Map<String, String> headers) throws IOException {
        long length = end - start + 1;
        if (length <= 0 || length > Integer.MAX_VALUE - 8) {
            throw new IOException("分段大小非法: " + start + "-" + end);
        }
        byte[] data = new byte[(int) length];
        int attempt = 0;
        int staleRetries = 0; // 陈旧连接的重连次数（不计入退避重试）
        long pos = start;
        while (true) {
            long before = pos;
            try {
                pos = fetchRange(endpoint, start, pos, end, data, info, headers);
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
                if (isStaleKeepAliveConnection(e) && staleRetries++ < MAX_STALE_RETRY) {
                    // 复用的长连接已被服务端空闲关闭：立即换新连接重试，不必等待退避
                    continue;
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
    private static long fetchRange(Endpoint endpoint, long start, long pos, long end, byte[] data,
                                   DownloadInfo info, Map<String, String> headers) throws IOException {
        // 【同一条 TCP 连接上发送多个 HTTP 请求】
        // open() 不再发送 Connection: close，且正常读完响应体后本方法也不会 disconnect()，
        // 于是 socket 会回到 HttpURLConnection 的全局 keep-alive 缓存。
        // 同一线程下一次请求同一 host:port 时，会直接复用这条 socket（HTTPS 下沿用同一 TLS 会话），
        // 即在同一条 TCP 连接上依次发送多个带不同 Range 的 GET 请求，无需重新握手。
        HttpURLConnection conn = open(endpoint, "bytes=" + pos + "-" + end, headers);
        long counted = 0; // 本次尝试已计入的字节，失败时回滚，避免重试重复计数
        boolean success = false;
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
                    counted += len;
                    info.addDownloaded(len); // 实时累加，不必等整个分段下载完
                }
            }
            if (current != end + 1) {
                if (current == pos) {
                    throw new IOException("服务器返回了空的 206 包体");
                }
                throw new IOException("分段未下载完整: " + current + "/" + (end + 1));
            }
            success = true;
            return current;
        } catch (IOException | RuntimeException e) {
            info.addDownloaded(-counted);
            throw e;
        } finally {
            if (!success) {
                // 失败/半途中断的连接不可复用，直接丢弃，避免污染连接池
                conn.disconnect();
            }
        }
    }

    /** 用 Range: bytes=0-0 探测总大小，同时确认服务器是否支持分段。 */
    private static Probe probe(Endpoint endpoint, Map<String, String> headers) throws IOException {
        log.info("获取资源: " + endpoint.url());
        while (true) {
            HttpURLConnection conn = open(endpoint, "bytes=0-0", headers);
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
    private static void downloadWhole(Endpoint endpoint, OutputStream out, DownloadInfo info,
                                      Map<String, String> headers) throws IOException {
        log.info("使用单线程下载资源: " + endpoint.url());
        while (true) {
            HttpURLConnection conn = open(endpoint, null, headers);
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
                        info.addDownloaded(len);
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
    private static HttpURLConnection open(Endpoint endpoint, String range, Map<String, String> headers) throws IOException {
        String current = endpoint.url();
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(current).toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestMethod("GET");
            if (headers != null) {
                headers.forEach(conn::setRequestProperty);
            }
            // 不设置 Connection: close —— HTTP/1.1 默认持久连接，读完响应体后连接可被复用
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
            // 记住重定向后的最终地址，后续分片直接请求它，避免每个分片都重新走一遍重定向链
            endpoint.update(current);
            return conn;
        }
        throw new IOException("重定向次数过多");
    }

    private static boolean isStaleKeepAliveConnection(Throwable t) {
        while (t != null) {
            if (t instanceof SocketException || t instanceof EOFException) {
                return true;
            }
            String message = t.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("unexpected end of stream")
                        || lower.contains("connection reset")
                        || lower.contains("connection abort")
                        || lower.contains("broken pipe")
                        || lower.contains("connection closed")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static int threadCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /** 每个分段大小：单个分段最大 100 MB，多余部分由空闲线程拆分。 */
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

    /**
     * 下载地址：重定向只在探测阶段解析一次，之后所有分片都直接请求最终地址，
     * 这样同一条长连接才能被后续分片持续复用。
     */
    private static final class Endpoint {
        private volatile String current;

        Endpoint(String url) {
            this.current = url;
        }

        String url() {
            return current;
        }

        void update(String resolved) {
            this.current = resolved;
        }
    }

    /** 绝对区间 [from, to)，to 不包含在内。 */
    private record Range(long from, long to) {
    }

    /** 把拉取到的分片数据写到目标（文件按绝对位置写，流写入分段缓冲）。 */
    private interface PieceWriter {
        void write(SegmentTask task, long offsetInSegment, byte[] data) throws IOException;
    }

    /**
     * 分段调度器。
     * <p>
     * 先按 chunk 切成固定分段排队下载；当某个线程完成任务而队列里已经没有待领取的片段时，
     * 它会把剩余数据最多的分段的未领取部分对半切走（最小切到 1KB），帮助其它线程完成下载。
     */
    private static final class SegmentScheduler {
        private final Endpoint endpoint;
        private final long total;
        private final int count;
        private final long chunk;
        private final DownloadInfo info;
        private final boolean ordered;
        private final Map<String, String> headers;
        private final PieceWriter writer;

        private final Object lock = new Object();
        private final List<SegmentTask> tasks = new ArrayList<>();
        private final ArrayDeque<Job> queue = new ArrayDeque<>();
        private final BlockingQueue<SegmentTask> completed = new LinkedBlockingQueue<>();
        private volatile Throwable error;
        private volatile int jobsRemaining;
        private volatile boolean finished;
        private int nextSegment;

        SegmentScheduler(Endpoint endpoint, long total, int count, long chunk,
                         DownloadInfo info, boolean ordered, Map<String, String> headers, PieceWriter writer) {
            this.endpoint = endpoint;
            this.total = total;
            this.count = count;
            this.chunk = chunk;
            this.info = info;
            this.ordered = ordered;
            this.headers = headers;
            this.writer = writer;
        }

        /** 创建并提交分段任务，最多创建到 limit 个分段。 */
        void openWindow(long limit) {
            synchronized (lock) {
                while (nextSegment < count && nextSegment < limit) {
                    int index = nextSegment++;
                    long start = index * chunk;
                    long end = Math.min(start + chunk, total);
                    SegmentTask task = new SegmentTask(index, start, end,
                            ordered ? new byte[(int) (end - start)] : null);
                    tasks.add(task);
                    queue.add(new Job(task, null));
                    jobsRemaining++;
                }
                lock.notifyAll();
            }
        }

        /** 领取任务：优先取队列，队列空了就去偷其它分段还没领取的剩余部分。 */
        Job nextJob() {
            synchronized (lock) {
                if (error != null) {
                    return null;
                }
                Job job = queue.poll();
                if (job != null) {
                    return job;
                }
                SegmentTask best = null;
                long bestRemain = 0;
                for (SegmentTask task : tasks) {
                    synchronized (task) {
                        long remain = task.end - task.claimed;
                        if (remain > bestRemain) {
                            bestRemain = remain;
                            best = task;
                        }
                    }
                }
                if (best == null) {
                    return null;
                }
                Range stolen;
                synchronized (best) {
                    stolen = best.steal();
                }
                if (stolen == null) {
                    return null;
                }
                jobsRemaining++;
                return new Job(best, stolen);
            }
        }

        Throwable execute(Job job) {
            if (job.range != null) {
                return fetchAndWrite(job.task, job.range);
            }
            while (true) {
                Range piece;
                synchronized (job.task) {
                    piece = job.task.claimPiece(PIECE_BYTES);
                }
                if (piece == null) {
                    return null;
                }
                Throwable failure = fetchAndWrite(job.task, piece);
                if (failure != null) {
                    return failure;
                }
            }
        }

        void jobFinished(Job job, Throwable failure) {
            boolean segmentDone;
            boolean allDone;
            synchronized (job.task) {
                segmentDone = job.task.taskDone();
            }
            synchronized (lock) {
                if (failure != null && error == null) {
                    error = failure;
                }
                jobsRemaining--;
                allDone = jobsRemaining == 0;
                if (segmentDone && ordered) {
                    completed.add(job.task);
                }
                lock.notifyAll();
            }
            if (allDone) {
                DEBUG_LOG.info("下载完成：最后结束的线程 {} (id={})，共 {} 字节", 
                        Thread.currentThread().getName(), Thread.currentThread().threadId(), total);
            }
        }

        boolean isFinished() {
            return error != null || finished;
        }

        /** 等待全部任务结束（出错时立即返回）；用于文件目标。 */
        void waitAllDone() throws InterruptedException {
            synchronized (lock) {
                while (jobsRemaining > 0 && error == null) {
                    lock.wait(100);
                }
                finished = true;
                lock.notifyAll();
            }
        }

        /** 通知工作线程退出。 */
        void markFinished() {
            synchronized (lock) {
                finished = true;
                lock.notifyAll();
            }
        }

        void awaitWork() throws InterruptedException {
            synchronized (lock) {
                if (isFinished()) {
                    return;
                }
                lock.wait(50);
            }
        }

        SegmentTask pollCompleted(long millis) throws InterruptedException {
            return completed.poll(millis, TimeUnit.MILLISECONDS);
        }

        Throwable error() {
            return error;
        }

        private Throwable fetchAndWrite(SegmentTask task, Range range) {
            DEBUG_LOG.info("线程 {} (id={}) 负责分段 #{} 区间 [{}, {})，共 {} 字节",
                    Thread.currentThread().getName(), Thread.currentThread().threadId(),
                    task.index, range.from, range.to, range.to - range.from);
            try {
                byte[] data = fetchSegment(endpoint, range.from, range.to - 1, info, headers);
                writer.write(task, range.from - task.start, data);
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        record Job(SegmentTask task, Range range) {
        }
    }

    /** 一个文件分段。分段未被领取完的尾部可以被其它线程拆走并发下载。 */
    private static final class SegmentTask {
        private final int index;
        private final long start;
        private long end;            // 终点（不含）；被帮助线程拆分时会向左收缩
        private final byte[] buffer; // 流目标的分段缓冲，文件目标为 null
        private long claimed;        // 已被领取到的绝对位置，后续只会向前推进
        private int running = 1;     // 该分段上尚未结束的任务数

        SegmentTask(int index, long start, long end, byte[] buffer) {
            this.index = index;
            this.start = start;
            this.end = end;
            this.buffer = buffer;
            this.claimed = start;
        }

        /** 领取不超过 maxPiece 的下一段；没有剩余时返回 null 表示该任务完成。 */
        synchronized Range claimPiece(long maxPiece) {
            if (claimed >= end) {
                return null;
            }
            long from = claimed;
            claimed = Math.min(from + maxPiece, end);
            return new Range(from, claimed);
        }

        /** 帮助线程从剩余部分偷一半；剩余不足 2KB 时整段拿走，不再细分（最小粒度 1KB）。 */
        synchronized Range steal() {
            long remain = end - claimed;
            if (remain <= 0) {
                return null;
            }
            long oldEnd = end;
            end = remain <= MIN_STEAL_BYTES * 2 ? claimed : claimed + remain / 2;
            running++;
            return new Range(end, oldEnd);
        }

        synchronized boolean taskDone() {
            running--;
            return running == 0 && claimed >= end;
        }
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
