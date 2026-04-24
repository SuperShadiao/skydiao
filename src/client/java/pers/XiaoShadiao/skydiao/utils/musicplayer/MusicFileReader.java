package pers.XiaoShadiao.skydiao.utils.musicplayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MusicFileReader extends FileInputStream {

    public int available;

    public MusicFileReader(File file) throws FileNotFoundException {
        super(file);
        try {
            available = super.available();
        } catch (IOException e) {
            throw new RuntimeException("无法获取available字节数");
        }
    }

    @Override
    public int read() throws IOException {
        available--;
        return super.read();
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        available -= (len + off);
        return super.read(b, off, len);
    }
    
    @Override
    public long skip(long n) throws IOException {
        long a = super.skip(n);
        available -= a;
        return a;
    }
    
    @Override
    public int available() throws IOException {
        available = Math.max(0, available);
        return available;
    }
    
}
