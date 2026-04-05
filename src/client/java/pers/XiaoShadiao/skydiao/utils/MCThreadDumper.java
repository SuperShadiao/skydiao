package pers.XiaoShadiao.skydiao.utils;

public class MCThreadDumper extends Thread {

    public static final MCThreadDumper INSTANCE = new MCThreadDumper();
    private Thread minecraftThread;
    private long lastTime = System.currentTimeMillis();
    private boolean printedFlag = false;

    private final Throwable printer = new Throwable("Dump Minecraft Thread");

    private MCThreadDumper() {
        setName("主线程卡死调试器");
        setDaemon(true);
        start();
    }

    @Override
    public void run() {
        if(!ToolList.getInstance().isXiaoShadiao()) return;
        while(true) {
            try {
                Thread.sleep(200);
                if(System.currentTimeMillis() - lastTime > 300) {
                    if(!printedFlag) {
                        printer.setStackTrace(minecraftThread.getStackTrace());
                        printer.printStackTrace();
                        printedFlag = true;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void flagAlive() {
        lastTime = System.currentTimeMillis();
        printedFlag = false;
    }

    public void setMinecraftThread(Thread minecraftThread) {
        this.minecraftThread = minecraftThread;
    }

}
