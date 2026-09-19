package pers.XiaoShadiao.skydiao.eventbuslistener.bilibili;

import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.BLiveEvent;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.blivesensitiveword.IDetectorAccessor;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;
import pers.XiaoShadiao.skydiao.utils.musicplayer.PlayerThread;
import pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.impl.MusicPlatform;
import top.mrxiaom.bili.live.runtime.data.Dm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BLiveReqMusicListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "BLiveReqMusicListener";
    }

    @Override
    public void registerListeners() {
        BLiveEvent.ON_RECEIVED_DANMAKU.register(this::onDm);
    }

    private boolean onDm(Dm dm) {
        if(dm.msg.startsWith("点歌")) {
            if (ConfigManager.musicplayer.getValue()) {
                String music = dm.msg.substring(2);

                addStarRailNotification("§f正在处理用户§6" + dm.userName + "§f点歌: §6" + music, StarRailNotification.Type.success);

                ToolList.addThreadedTask(() -> {
                    BilibiliMusicInfo result = null;

                    for (MusicPlatform platform : MusicPlatform.getMusicPlatforms()) {
                        if (!platform.canDownloadVIPMusic()) {
                            continue;
                        }

                        try {
                            List<MusicInfo> infos = platform.searchMusic(music);
                            if(!infos.isEmpty()) {
                                MusicInfo first = infos.getFirst();
                                platform.downloadMusic(first);
                                result = new BilibiliMusicInfo(first, dm);
                                break;
                            }
                        } catch (Exception e) {
                            logger.catching(e);
                        }
                    }

                    if(result != null) {
                        if(result.name.contains("义勇军")) {
                            addStarRailNotification("§6" + dm.userName + "§f你点的歌暂时无法播放, 换首音乐吧!", StarRailNotification.Type.warning);
                        } else if(PlayerThread.getBiliLiveMusics().contains(result) || PlayerThread.currentMusic.equals(result)) {
                            addStarRailNotification("§6" + dm.userName + "§f这首音乐§e" + result + "§f已经在队列中, 请勿重复点歌!", StarRailNotification.Type.warning);
                        } else {
                            PlayerThread.addBiliLiveMusic(result);
                            addStarRailNotification("§f用户§6" + dm.userName + "§f点歌成功! §e" + result + "§f, 请等待当前音乐播放完毕", StarRailNotification.Type.success);
                        }
                    } else {
                        addStarRailNotification("§6" + dm.userName + "§f音乐无搜索结果或下载失败, 重试或者换首音乐吧!", StarRailNotification.Type.warning);
                    }
                    return null;
                });
            } else {
                addStarRailNotification("主播尚未开启点歌台, 无法进行点歌!", StarRailNotification.Type.warning);
            }
        } else if(dm.msg.startsWith("撤销点歌")) {
            a:{
                Iterator<MusicInfo> it = PlayerThread.getBiliLiveMusics().iterator();
                List<MusicInfo> templist = new ArrayList<>();
                while(it.hasNext()) {
                    MusicInfo mi = it.next();
                    templist.addFirst(mi);
                }
                it = templist.iterator();
                while(it.hasNext()) {
                    MusicInfo mi = it.next();
                    if(mi instanceof BilibiliMusicInfo bilibiliMusicInfo && bilibiliMusicInfo.dm.openId.equals(dm.openId)) {
                        addStarRailNotification("§f用户§6" + dm.userName + "§f撤销点歌成功! §e" + mi, StarRailNotification.Type.success);
                        PlayerThread.removeBiliLiveMusic(mi);
                        break a;
                    }
                }
                if(PlayerThread.currentMusic instanceof BilibiliMusicInfo bilibiliMusicInfo && bilibiliMusicInfo.dm.openId.equals(dm.openId)) {
                    addStarRailNotification("§f用户§6" + dm.userName + "§f撤销点歌成功! §b" + PlayerThread.currentMusic, StarRailNotification.Type.success);
                    PlayerThread.destoryCurrent();
                } else addStarRailNotification("§f用户§6" + dm.userName + "§f撤销点歌失败! 没有找到你点的歌!", StarRailNotification.Type.warning);
            }
        } else if(dm.msg.startsWith("切歌")) {
            if(PlayerThread.currentMusic instanceof BilibiliMusicInfo bilibiliMusicInfo && bilibiliMusicInfo.dm.openId.equals(dm.openId)) {
                addStarRailNotification("§f用户§6" + dm.userName + "§f切歌成功! §e" + PlayerThread.currentMusic, StarRailNotification.Type.success);
                PlayerThread.destoryCurrent();
            } else {
                addStarRailNotification("§f用户§6" + dm.userName + "§f切歌失败! 当前播放的不是你的歌!", StarRailNotification.Type.warning);
            }
        }
        return false;
    }

    private static void addStarRailNotification(String s, StarRailNotification.Type type0) {
        XSDHUD.starRailNotification.updateMessage(IDetectorAccessor.getInstance().scanIllegalWords(s, true, true).transfered, type0);
    }

}
