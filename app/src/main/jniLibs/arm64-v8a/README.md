把 Android 版 Stockfish 放到这里，文件名必须是：

    libstockfish.so

获取方式（任选其一）：

1. 跑脚本（需要 curl + tar）：

       ./tools/fetch_stockfish.sh

2. 手动下载：

   https://github.com/official-stockfish/Stockfish/releases
   → 找到 stockfish-android-armv8.tar
   → 解包后把 stockfish-android-armv8 改名为 libstockfish.so 放到本目录

为什么必须叫 .so：Android 只允许执行 APK 安装时解压到 nativeLibraryDir
的 `lib*.so`。改成别的名字或放进 assets 都无法 exec。

为什么不在仓库里：这个文件约 76MB，二进制不入库。
