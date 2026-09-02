package pers.XiaoShadiao.skydiao.utils;

import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class URLFetchProcess extends FilterInputStream {

    // 1. 计数器：记录已读取的字节数
    private long readByteCount = 0;
    // 2. 状态标记：流是否已经结束
    private boolean done = false;
    // 3. 状态标记：是否是因为异常而结束
    private boolean failed = false;

    public URLFetchProcess(InputStream in) {
        super(in);
    }

    // 重写单字节读取
    @Override
    public int read() throws IOException {
        try {
            int b = super.read();
            if (b != -1) {
                readByteCount++; // 成功读到数据，计数 +1
            } else {
                done = true; // 读到 -1，正常结束
            }
            return b;
        } catch (IOException e) {
            done = true;
            failed = true;
            throw e;
        }
    }

    // 重写批量读取（保证批量读取时计数和状态都准确）
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            int n = super.read(b, off, len);
            if (n != -1) {
                readByteCount += n; // 累加实际读取到的字节数
            } else {
                done = true;
            }
            return n;
        } catch (IOException e) {
            done = true;
            failed = true;
            throw e;
        }
    }

    // 重写 readAllBytes
    @Override
    public byte[] readAllBytes() throws IOException {
        try {
            byte[] result = super.readAllBytes();
            readByteCount += result.length; // 记录全部读取的长度
            done = true;
            return result;
        } catch (IOException e) {
            done = true;
            failed = true;
            throw e;
        }
    }

    public String getProcessString() {
        return ToolList.getInstance().numberToByteString(readByteCount);
    }

    public boolean isDone() {
        return done;
    }

    public boolean isFailed() {
        return failed;
    }

    public URLFetchProcess addToTitle() {
        AbstractListener.titleChanger.downloadProcess.addProcess(this);
        return this;
    }

}
