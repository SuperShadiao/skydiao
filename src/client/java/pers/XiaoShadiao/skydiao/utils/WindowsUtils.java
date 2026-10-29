package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLVideo;

public class WindowsUtils {

    public static boolean isWindowsFocused() {
        return ToolList.mc.getWindow().isFocused();
    }

    public static boolean isWindowsIconfied() {
        return ToolList.mc.getWindow().isIconified();
    }

    public static void focusWindows() {
        ToolList.mc.execute(() -> {
            Window window = ToolList.mc.getWindow();
            if(window.isIconified()) SDLVideo.SDL_RestoreWindow(window.handle());
            SDLVideo.SDL_RaiseWindow(window.handle());
        });
    }

}
