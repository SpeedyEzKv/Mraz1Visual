package dev.mraz1visual.compat;

import java.nio.file.Path;

/**
 * Система вокруг игры: ссылки, папки, частота монитора, системные диалоги выбора файлов.
 * 26.3 перешла с GLFW на SDL3: нет tinyfd (диалоги) и GLFW-мониторов — здесь их замены.
 */
public final class Platform {
    private Platform() {}

    public static void openUri(String uri) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(uri));
        *///?} else {
        net.minecraft.util.Util.getPlatform().openUri(uri);
        //?}
    }

    public static void openPath(Path path) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openPath(path);
        *///?} else {
        net.minecraft.util.Util.getPlatform().openPath(path);
        //?}
    }

    /** Частота основного монитора, 0 — неизвестно. */
    public static int monitorHz() {
        try {
            //? if >=26.3 {
            /*var m = net.minecraft.client.Minecraft.getInstance().getWindow().findBestMonitor();
            return m == null ? 0 : Math.round(m.currentMode().getRefreshRate());
            *///?} else {
            long mon = org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor();
            org.lwjgl.glfw.GLFWVidMode mode = mon == 0 ? null : org.lwjgl.glfw.GLFW.glfwGetVideoMode(mon);
            return mode == null ? 0 : mode.refreshRate();
            //?}
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Системный диалог «выбрать папку». null — отменили или диалогов нет (26.3: тогда открывается fallback). */
    public static String pickFolder(String title, Path fallback) {
        //? if >=26.3 {
        /*if (fallback != null) openPath(fallback);
        return null;
        *///?} else {
        return org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_selectFolderDialog(title, System.getProperty("user.home"));
        //?}
    }

    /**
     * Системный диалог «открыть файлы». patterns — маски (*.zip), multi — несколько файлов (через «|»).
     * 26.3: диалога нет — открывается папка fallback, куда можно положить файлы вручную.
     */
    public static String pickFiles(String title, String[] patterns, String description, boolean multi, Path fallback) {
        //? if >=26.3 {
        /*if (fallback != null) openPath(fallback);
        return null;
        *///?} else {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (String p : patterns) filters.put(stack.UTF8(p));
            filters.flip();
            return org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, description, multi);
        }
        //?}
    }

    /** Есть ли системные диалоги выбора файлов (до 26.3 — да). */
    public static boolean hasFileDialogs() {
        //? if >=26.3 {
        /*return false;
        *///?} else {
        return true;
        //?}
    }
}
