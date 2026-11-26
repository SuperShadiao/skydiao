package pers.XiaoShadiao.skydiao.utils.devdebugger;

/**
 * 本地调试器动态加载的探针契约。
 *
 * <p>上传的类必须位于非默认包、拥有无参构造器并实现本接口。运行时会被
 * {@link LocalhostHttpDebugger} 使用独立的 {@code URLClassLoader} 加载，
 * 父加载器为模组自身的加载器，因此探针可以直接引用游戏与模组里的类型。
 *
 * <p>返回值约定：
 * <ul>
 *   <li>{@code byte[]} —— 原样作为响应体回传，配合 {@code ?type=image/png} 可直接导出图片；</li>
 *   <li>其他对象 —— 由 Gson 序列化后放进 {@code {"ok":true,"result":...}}；</li>
 *   <li>抛出异常 —— 回传 500 与完整堆栈，游戏不受影响。</li>
 * </ul>
 *
 * <p>默认探针在客户端主线程执行，因此直接写游戏逻辑即可，不要再调用
 * {@code Minecraft.execute}，否则会死锁。只读字段的探针可以传
 * {@code ?thread=io} 完全脱离主线程执行。
 */
public interface DebugProbe {

    Object run(DebugContext ctx) throws Throwable;
}
