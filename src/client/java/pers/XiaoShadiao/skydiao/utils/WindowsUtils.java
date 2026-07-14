package pers.XiaoShadiao.skydiao.utils;

import org.lwjgl.glfw.GLFW;

public class WindowsUtils {

    public static boolean isWindowsFocused() {
        return GLFW.glfwGetWindowAttrib(ToolList.mc.getWindow().handle(), GLFW.GLFW_FOCUSED) != 0;
    }

    public static boolean isWindowsIconfied() {
        return GLFW.glfwGetWindowAttrib(ToolList.mc.getWindow().handle(), GLFW.GLFW_ICONIFIED) != 0;
    }

    public static void focusWindows() {
        ToolList.mc.execute(() -> {
            long handle = ToolList.mc.getWindow().handle();
            if(isWindowsIconfied()) GLFW.glfwRestoreWindow(handle);
            GLFW.glfwFocusWindow(handle);
        });
    }

}
