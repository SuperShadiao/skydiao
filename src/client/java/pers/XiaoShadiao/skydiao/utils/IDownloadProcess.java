package pers.XiaoShadiao.skydiao.utils;

import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

public interface IDownloadProcess {

    public long getTotalByteSize();

    public long getDownloadedByteSize();

    public boolean isDone();

    public boolean isMaxByteSizeUnknown();

    public default String getTotalByteSizeString() {
        return numberToByteString(getTotalByteSize());
    }

    public default String getDownloadedByteSizeString() {
        return numberToByteString(getDownloadedByteSize());
    }

    public default <T extends IDownloadProcess> T addToTitle() {
        AbstractListener.titleChanger.downloadProcess.addProcess(this);
        return (T) this;
    }

    private String numberToByteString(long l) {
        String[] sl = {"B","KB","MB","GB","TB"};
        double d2 = l;

        for(String s : sl) {
            d2 /= 1024;
            if(d2 < 1) {
                return (Math.round(d2 * 1024d * 100d) / 100d) + s;
            }
        }

        return (Math.round(d2 * 1024d * 100d) / 100d) + sl[sl.length - 1];
    }

}
