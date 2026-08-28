package pers.XiaoShadiao.skydiao.utils.musicplayer;

import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.File;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayerThread {

    public static MusicStatus current;
    private static long lastResponse;

    private static int musicPlayerAccessor;
    
    static Queue<MusicInfo> musicFixQueue = new ConcurrentLinkedQueue<>();
    
    private static Thread playThread;
    
    private static boolean playing;

    private static Queue<MusicInfo> playQueue = new ConcurrentLinkedQueue<>();

    public static MusicInfo currentMusic;
    private static MusicInfo currentMusic2;
    
    public static boolean switchMusicFlag; 
    
    private static int[] randomSortedMusic = new int[0];
    
    public static int musicflag;
    
    static {
        MusicListManager.loadMusicFromFolder();
    }

    public static void destoryCurrent() {
        switchMusicFlag = true;
    }

    public static void clearQueue() {
        playQueue.clear();
    }
    
    public static void playMI(MusicInfo mi) {
        clearQueue();
        playQueue.add(mi);
        try {
            current.getPlayer().stop();
        } catch(Exception e) {}
        startPlay();
    }

    public static void startPlay() {
        ConfigManager.musicplayer.setValue(true);
        playing = true;
        playThread.interrupt();
    }

    public static void stopPlay() {
        ConfigManager.musicplayer.setValue(false);
        playing = false;
        try {
            current.getPlayer().stop();
        } catch(Exception e) {}
        destoryCurrent();
    }
    
    public static boolean isPlaying() {
        return playing;
    }
    
    public static void executeThread() {
        
        int thisTokenAcc = musicPlayerAccessor = ToolList.getInstance().random.nextInt();
        List<MusicInfo> musiclist = MusicListManager.getMusics();

        try {
            playQueue.add(musiclist.get(ConfigManager.musiclastmusic.getValue()));
        } catch(Exception e) {};
        
        while(thisTokenAcc == musicPlayerAccessor) {
            
            try {
                musiclist = MusicListManager.getMusics();
                if (musiclist.isEmpty()) {
                    ConfigManager.musicplayer.setValue(false);
                }
                
                playing = ConfigManager.musicplayer.getValue();
                lastResponse = System.currentTimeMillis();
                if(playing) {
                    // fixers.get(ToolList.getInstance().random.nextInt(fixers.size())).interrupt();
                    
                    if(musiclist.size() != randomSortedMusic.length) {
                        randomSortedMusic = new int[musiclist.size()];
                        for(int i = 0;i < randomSortedMusic.length;i++) {
                            randomSortedMusic[i] = i;
                        }
                        for(int i = 0;i < randomSortedMusic.length;i++) {
                            int index = ToolList.getInstance().random.nextInt(randomSortedMusic.length);
                            
                            int temp = randomSortedMusic[index];
                            randomSortedMusic[index] = randomSortedMusic[i];
                            randomSortedMusic[i] = temp;
                        }
                        
                    }
                    int playMode = ConfigManager.musicplayermode.getValue();
                    if(playQueue.isEmpty()) {
                        switch(playMode) {
                            case 0:
                                if(musiclist.indexOf(currentMusic2) + 1 == musiclist.size()) playQueue.addAll(musiclist);
                                else playQueue.addAll(musiclist.subList(musiclist.indexOf(currentMusic2) + 1, musiclist.size()));
                                
                                break;
                            case 1:
                                for(int i : randomSortedMusic) {
                                    playQueue.add(musiclist.get(i));
                                }
                                break;
                            case 2:
                                for(int ignored : randomSortedMusic) {
                                    playQueue.add(musiclist.get(ToolList.getInstance().random.nextInt(randomSortedMusic.length)));
                                }
                                break;
                            default:
                                playQueue.add(currentMusic2 == null ? MusicListManager.getMusics().iterator().next() : currentMusic2);
                        }
                    }
                    MusicInfo temp = playQueue.peek();
                    playQueue.removeIf(a -> a.isDisabled);

                    if((currentMusic = currentMusic2 = playQueue.poll()) == null && (currentMusic = currentMusic2 = temp) == null) {
                        stopPlay();
                    } else {
                        ConfigManager.musiclastmusic.setValue(musiclist.indexOf(currentMusic));
                        play(currentMusic);
                    }
                } else {
                    lastResponse = System.currentTimeMillis();

                    Thread.sleep(3000);
                }
            } catch(Throwable e) {
                if(e instanceof InterruptedException) {
                    ToolList.getInstance().log.warn("InterruptedException!");
                } else {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void createThread() {
        threadStatus_SetAlive();

        try {
            playThread.interrupt();
        } catch (Exception ignored) {

        }

        playThread = new Thread(PlayerThread::executeThread);
        playThread.setName("XSD MusicPlayer");
        playThread.setPriority(Thread.MAX_PRIORITY);
        playThread.start();
    }
    
    public static boolean threadStatus_Died() {
        return System.currentTimeMillis() - lastResponse > 10000;
    }

    public static void threadStatus_SetAlive() {
        lastResponse = System.currentTimeMillis(); 
    }

    public static void fixMIFileVeriable(MusicInfo mi) {
        if(mi.musicFile == null || mi.musicLyric == null || mi.imgFile == null)  {
            mi.musicLyric = new File(ConfigManager.hypixelhelpermusicfolder.getValue(), mi.type.toUpperCase() + "_" + mi.hashOrID + "_lyric.txt");
            mi.musicFile = new File(ConfigManager.hypixelhelpermusicfolder.getValue(), mi.type.toUpperCase() + "_" + mi.hashOrID + ".mp3");
            mi.imgFile = new File(ConfigManager.hypixelhelpermusicfolder.getValue(), mi.type.toUpperCase() + "_" + mi.hashOrID + ".png");
        }
    }

    public static void play(String idOrHash, String musicInfo, File music, File lyric) {
        play(idOrHash, musicInfo, music, lyric, null, null);
    }

    public static void play(MusicInfo mi) {
        fixMIFileVeriable(mi);
        play(mi.hashOrID, mi.name + " - " + mi.singer, mi.musicFile, mi.musicLyric, null, mi);
    }
    
    public static void play(String idOrHash, String musicInfo, File music, File lyric, MusicStatus ms, MusicInfo mi) {
        try {
            current.getPlayer().stop();
            current.getPlayer().close();
        } catch (Exception ignored) {
        } finally {

            String musicInfo1 = musicInfo;
            musicInfo1 = musicInfo1.length() > 20 ? musicInfo1.substring(0, 20) + "..." : musicInfo1;
            try {
                current = ms == null ? new MusicStatus(music, lyric) : ms;
                current.getPlayer().play();
            } catch (Throwable e) {
                e.printStackTrace();
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e3) {
                }
            }
        }
    }

}