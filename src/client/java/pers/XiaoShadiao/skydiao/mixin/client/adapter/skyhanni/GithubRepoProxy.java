package pers.XiaoShadiao.skydiao.mixin.client.adapter.skyhanni;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(targets = {
        "at.hannibal2.skyhanni.utils.api.ApiStaticPostPath",
        "at.hannibal2.skyhanni.utils.api.ApiStaticGetPath",
        "at.hannibal2.skyhanni.utils.api.ApiStaticPath"
})
public class GithubRepoProxy {

    @WrapMethod(method = "getUrl")
    public String getUrl(Operation<String> original) {
        String call = original.call();
        if(call.contains("github.com")) {
            call = ToolList.getInstance().wrapAsGithubProxy(call);
        }
        return call;
    }

}
