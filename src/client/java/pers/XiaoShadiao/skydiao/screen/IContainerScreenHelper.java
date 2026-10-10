package pers.XiaoShadiao.skydiao.screen;

public interface IContainerScreenHelper {

    public default int getIndexByLineCol(int line, int col) {
        checkIndex(line - 1, col - 1);
        return (line - 1) * 9 + (col - 1);
    }

    public default int getIndexByLineColIndex(int line, int col) {
        checkIndex(line, col);
        return line * 9 + col;
    }

    public default int[] getLineColByIndex(int index) {
        return new int[] {index / 9, index % 9};
    }

    public default int[] getLineColIndexByIndex(int index) {
        return new int[] {index / 9 - 1, index % 9 - 1};
    }

    public default void checkIndex(int line, int col) {
        if(line < 0 || line >= 6) {
            throw new IllegalArgumentException("Line out of range " + line);
        }
        if(col < 0 || col >= 9) {
            throw new IllegalArgumentException("Col out of range " + col);
        }
    }

}
