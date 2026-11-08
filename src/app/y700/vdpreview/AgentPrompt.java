package app.y700.vdpreview;

import android.view.Display;

/**
 * 给别的 code agent 的操作说明。控制页点一次就复制全文。
 * 号会变，正文要求每次现查，末尾再附上这一次查到的状态。
 */
final class AgentPrompt {
    private AgentPrompt() {
    }

    static String text(Display display, String packageName) {
        StringBuilder body = new StringBuilder();
        body.append("你在操作一台 Legion Y700（Android 16）上的虚拟屏。用户的主屏正在做别的事，不要占用它。\n\n");
        body.append("连接用 ssh y700，这是 root。不要用 adb。\n\n");
        body.append("虚拟屏的名字是 y700-agent，尺寸 1080x1920，密度 420。它由 shell 进程 com.y700.vd.Main 撑着，dex 在 /data/local/tmp/vd.dex。进程在，屏才在。不要杀掉这个进程，不要销毁这块屏。屏一拆，上面的应用会掉回主屏。\n\n");
        body.append("逻辑显示号不固定，不要写死 7。每次操作前先查名字等于 y700-agent 的那块屏，下面用 $D 表示查到的逻辑号。\n\n");
        body.append("查逻辑号：\n");
        body.append("ssh y700 'dumpsys display' \n");
        body.append("在输出里找 uniqueId 含 y700-agent 的 DisplayDeviceInfo，再看它的 mDisplayId。\n\n");
        body.append("主屏是显示 0。点击、滑动、按键、启动应用都必须带 $D。不带 -d 或 --display 会打到主屏。\n\n");
        body.append("点击和按键：\n");
        body.append("ssh y700 'input -d $D tap X Y'\n");
        body.append("ssh y700 'input -d $D swipe X1 Y1 X2 Y2 250'\n");
        body.append("ssh y700 'input -d $D keyevent KEYCODE'\n");
        body.append("X Y 按当次截图重算，单位是虚拟屏像素。不要沿用上一次的坐标。\n\n");
        body.append("截图不能写 screencap -d $D，那个号无效。先查 SurfaceFlinger 的长号：\n");
        body.append("ssh y700 'dumpsys SurfaceFlinger --display-id'\n");
        body.append("用 displayName=\"y700-agent\" 那一行前面的数字：\n");
        body.append("ssh y700 'screencap -d 长号 -p /data/local/tmp/vd-agent.png'\n\n");
        body.append("截图全黑时，显示组休眠了。只这样唤醒，把 $D 换成当前逻辑号：\n");
        body.append("ssh y700 'CLASSPATH=/data/local/tmp/wakegroup.dex app_process /system/bin com.y700.vd.WakeGroup $D'\n");
        body.append("输出要从 BEFORE false 变成 AFTER true。不要对这块屏做关再开，会留下一层 ColorFade，画面还是黑的。\n\n");
        body.append("把应用打开到虚拟屏：\n");
        body.append("ssh y700 'am start --display $D -n 包名/Activity'\n");
        body.append("打开后立刻看这个 Activity 的 mDisplayId。如果它出现在显示 0，马上移回 $D，并把主屏原来的应用拉回来。做完再确认主屏焦点仍是用户原来的应用。\n\n");
        body.append("中文不要用 input text。把字写进平板上的文件，再：\n");
        body.append("ssh y700 'CLASSPATH=/data/local/tmp/clip.dex app_process /system/bin com.y700.clip.ClipSet 文件路径'\n");
        body.append("必须看到 CLIP_OK。然后点进输入框，再 input -d $D keyevent 279。279 是粘贴。ClipSet 会去掉文件末尾的一个换行。\n\n");
        body.append("桌面应用 ");
        body.append(packageName);
        body.append(" 只负责看浮窗和挑选要打开的应用。点浮窗里的画面不会把点击送进虚拟屏。不要 force-stop 它。\n\n");
        body.append("不要做这些事：\n");
        body.append("- 不要点主屏，不要换掉用户正在用的应用。\n");
        body.append("- 不要在用户正在编辑的页面按返回键。keyevent 4 会退出编辑。\n");
        body.append("- 不要重建以前的显示号 6，不要动显示号 3。3 是应用宝的 1x1 私有屏。\n");
        body.append("- 不要把 persist.sys.lenovo.is_test_mode 设成 true。设成 true 之后，系统截图会变成空图。\n");
        body.append("- 不要删 /data/local/tmp/clip.dex、vd.dex、wakegroup.dex。\n");
        body.append("- 不要用无障碍，不要用 uiautomator dump。\n");
        body.append("- 持有进程活着时不要再开一个。它不在了才用这条拉起：\n");
        body.append("  ssh y700 '/debug_ramdisk/magisk su 2000 -c \"setsid /system/bin/sh -c \\\"CLASSPATH=/data/local/tmp/vd.dex app_process /system/bin com.y700.vd.Main >/data/local/tmp/vd3.log 2>&1 </dev/null\\\" & echo STARTED\"'\n\n");
        body.append("当前查到的状态，下次操作前重新查，不要沿用这个号：\n");
        if (display == null) {
            body.append("这次没有找到名为 y700-agent 的虚拟屏。先确认 com.y700.vd.Main 还在。\n");
        } else {
            String state = display.getState() == Display.STATE_ON ? "开着" : "休眠";
            body.append("名字 y700-agent，逻辑号 ");
            body.append(display.getDisplayId());
            body.append("，");
            body.append(display.getMode().getPhysicalWidth());
            body.append("x");
            body.append(display.getMode().getPhysicalHeight());
            body.append("，");
            body.append(state);
            body.append("。\n");
        }
        return body.toString();
    }
}
