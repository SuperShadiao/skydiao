package pers.XiaoShadiao.skydiao.api.v1;

import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.XSDSafeSession;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SkydiaoAPI {

    private SkydiaoAPI() {

    }

    public static final SkydiaoAPI INSTANCE = new SkydiaoAPI();

    public CompletableFuture<Boolean> checkTokenVaild() {
        if(ToolList.mc.getUser() instanceof XSDSafeSession xsdSafeSession) {
            return xsdSafeSession.checkTokenVaild();
        } else {
            return CompletableFuture.failedFuture(new AssertionError(ToolList.mc.getUser().getClass().getName()));
        }
    }

    public CompletableFuture<Boolean> checkTokenVaild(UUID uuid) {
        if(ToolList.mc.getUser() instanceof XSDSafeSession xsdSafeSession) {
            return xsdSafeSession.checkTokenVaild(uuid);
        } else {
            return CompletableFuture.failedFuture(new AssertionError(ToolList.mc.getUser().getClass().getName()));
        }
    }

}
