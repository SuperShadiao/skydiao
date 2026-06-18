package pers.XiaoShadiao.skydiao.mixin.client.adapter.skyhanni;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Mixin(targets = "at.hannibal2.skyhanni.utils.NeuItems")
public class RepoLoaderStuckFix {

    @WrapMethod(method = "readAllNeuItems")
    private void readAllNeuItems(Operation<Void> original) {
        ToolList.getInstance().log.info("小沙雕的加载Skyhanni的Repo日志输出 - 开始");
        FutureTask<Void> task = new FutureTask<>(original::call);
        Thread thread = new Thread(task);
        thread.start();

        try {
            task.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Throwable t = new Throwable("载入线程位置");
            t.setStackTrace(thread.getStackTrace());
            RuntimeException r = new RuntimeException(e);
            e.addSuppressed(t);
            throw r;
        }
        ToolList.getInstance().log.info("小沙雕的加载Skyhanni的Repo日志输出 - 完成");
    }

}
