package pers.XiaoShadiao.skydiao.mixin.client.adapter.firmament;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import moe.nea.firmament.util.net.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Mixin(targets = "moe.nea.firmament.util.net.HttpUtil")
public class FirmamentGithubRepoProxy {

    @WrapMethod(method = "request(Ljava/net/URI;)Lmoe/nea/firmament/util/net/HttpUtil$Request;")
    public HttpUtil.Request request(URI url, Operation<HttpUtil.Request> original) {
        if (url.getHost().contains("github.com")) {
//            try(InputStream is = ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club")) {
//                is.readAllBytes();
//
//                String proxy = "https://xiaoshadiao.club/datagetter?url=" + URLEncoder.encode(url.toString(), StandardCharsets.UTF_8);
//                ToolList.getInstance().log.info(proxy);
//                url = URI.create(proxy);
//            } catch (Throwable e) {
//                e.printStackTrace();
//            }
            url = ToolList.getInstance().wrapAsGithubProxy(url);
        }
        return original.call(url);
    }

}
