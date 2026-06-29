package pers.XiaoShadiao.skydiao.eventbuslistener;

import pers.XiaoShadiao.skydiao.irc.ChatPacket;

public interface ICustomSkinModelLoader {
    public boolean isSupportYSM();
    public void handleIRCPacket(ChatPacket packet);
    public void resendSwitchPacket();
}