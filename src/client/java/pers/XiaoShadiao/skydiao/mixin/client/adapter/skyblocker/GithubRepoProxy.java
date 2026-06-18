package pers.XiaoShadiao.skydiao.mixin.client.adapter.skyblocker;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(targets = "de.hysky.skyblocker.utils.NEURepoManager")
public class GithubRepoProxy {

    @Shadow @Final @Mutable
    private static String REMOTE_REPO_URL = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO.git";

    static {
        REMOTE_REPO_URL = ToolList.getInstance().wrapAsGithubProxy(REMOTE_REPO_URL);
    }

}
