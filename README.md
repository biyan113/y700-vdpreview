# 虚拟屏

联想拯救者平板 Legion Y700 上用来看虚拟屏。桌面名称是「虚拟屏」，包名 `app.y700.vdpreview`，版本 1.2。

这个应用不创建、也不销毁虚拟屏。屏的名字必须是 `y700-agent`，由 shell 进程 `com.y700.vd.Main` 撑着，dex 在 `/data/local/tmp/vd.dex`。进程在，屏才在。屏一拆，上面的应用会掉回主屏。

## 使用

安装后到系统设置里允许「显示在其他应用的上层」。Android 13 及以上还要允许通知，浮窗靠前台服务挂着。

打开桌面图标「虚拟屏」。

顶部卡片：

- 写着「虚拟屏没开着」时，点应用不会打开。先确认 `com.y700.vd.Main` 还在。
- 开着或休眠时，会显示逻辑号和分辨率。逻辑号会变，不要写死。
- 「打开」把上次选中的应用再打开到 `y700-agent`。
- 「看画面」在主屏上显示浮窗。
- 「收起浮窗」只去掉浮窗。虚拟屏里的应用还在。

浮窗：

- 拖顶部标题栏移动，拖右下角缩放。位置和大小会记住。
- 点浮窗里的画面不会把点击送进虚拟屏。
- 「换应用」回到选择页。「缩小」收成一条，「展开」恢复。「关闭」等同收起浮窗。
- 标题是「虚拟屏」加上当前逻辑号。屏休眠时后面带「休眠」。找不到屏时是「虚拟屏没开」。
- 画面读的是本应用私有目录里的 `files/live.png`，大约每 400 毫秒换一次。这个应用自己不截图。外面的进程要把画面写进这个文件，浮窗才有内容。包名是 `app.y700.vdpreview`。

给 code agent：

- 点「复制提示词」，操作说明进剪贴板，可以直接贴给别的 code agent。
- 说明要求每次现查 `y700-agent` 的逻辑号。点击、滑动、按键、启动应用都要带这个号，避免打到主屏。
- 「展开全文」可以在页面里看完。复制失败时，长按正文也能复制。

和谁通讯：

- 列出带启动入口的应用。点一行，就在虚拟屏里打开，并自动显示浮窗。选中的一行标成「当前」。
- 搜索框按名称或包名过滤。
- 系统列不出应用时，点「刷新」。刷新经 `/debug_ramdisk/magisk su` 执行 `sh /data/local/tmp/vd-launchers.sh`。成功后读取本应用私有目录的 `files/launchers.txt`，以及 `/data/local/tmp/vd-launchers.txt`。每行是 `包名`、制表符、`名称`。
- 本应用自己不会出现在列表里。

## 平板上要有

- 已经存在名为 `y700-agent` 的虚拟屏。
- 允许本应用显示在其他应用上层。
- 要刷新应用列表时，平板上要有 `/data/local/tmp/vd-launchers.sh`，并且 Magisk 允许 root。
- Android 8.0 及以上。

## 自己编译

不需要 Gradle。Mac 上 Android SDK 在 `~/Library/Android/sdk` 时直接：

```sh
./build.sh
```

也可以设置 `ANDROID_HOME`。SDK 里要有 `platforms;android-36` 和 `build-tools;36.0.0`，JDK 用 17。

编完得到 `vdpreview.apk`。签名文件是仓库根目录的 `debug.keystore`，没有就自动生成。库密码和密钥密码都是 `android`。keystore、apk 和 `build/` 不进 git。

```sh
adb install -r vdpreview.apk
```
