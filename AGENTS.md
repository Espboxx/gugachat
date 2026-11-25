# GuGuChat Android Agent Guide

Guide for building, unit testing, and performing interactive visual testing on **MuMuPlayer (MuMu 模拟器)** for the GuGuChat native Android repository (`com.gugachat.app`).

---

## 1. Environment & Gradle Build / Unit Tests

- **Required JDK**: JDK 17 (`/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`)
- **Gradle Wrapper**: `8.14.3` (`./gradlew`)
- **Network / Mirrors**: `settings.gradle.kts` prioritizes Aliyun & Tencent Maven mirrors and `gradle.properties` sets `-Djava.net.preferIPv4Stack=true`.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home

# Run all pure-JVM unit tests (MockWebServer, 4-protocol codec, chat/novel/rp/image domain)
./gradlew :app:testDebugUnitTest --no-daemon --console=plain

# Build debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug --no-daemon --console=plain
```

- XML test reports are written to `app/build/test-results/testDebugUnitTest/TEST-*.xml`.

---

## 2. MuMu 模拟器（MuMuPlayer）连接与管理

### 查询与启动模拟器实例

MuMuPlayer macOS 控制工具路径：

```bash
MUMUTOOL="/Applications/MuMuPlayer.app/Contents/MacOS/mumutool"

# 查看所有模拟器实例状态与 adb_port（默认 index=0 "我的安卓", adb_port=16384）
"$MUMUTOOL" info all

# 启动 / 关闭指定设备（通常 index 为 0）
"$MUMUTOOL" open 0
"$MUMUTOOL" close 0
```

### 连接 ADB 与端口映射

```bash
# 连接 MuMu 模拟器默认 ADB 端口
adb connect 127.0.0.1:16384
adb devices

# 若本机运行本地服务或 Mock 测试服务（如 127.0.0.1:18080），映射给模拟器访问：
adb -s 127.0.0.1:16384 reverse tcp:18080 tcp:18080

# 测试结束后务必清理端口转发：
adb -s 127.0.0.1:16384 reverse --remove-all
```

> **Note (zsh)**: 在 `zsh` 中不要将 `ADB="adb -s 127.0.0.1:16384"` 当作未分割的变量直接执行（zsh 默认不按空格分词），请直接写 `adb -s 127.0.0.1:16384 ...` 或使用数组 `ADB=(adb -s 127.0.0.1:16384)`。

---

## 3. 安卓交互与视觉测试流程（MuMu）

### 安装、重置与启动 App

```bash
# 覆盖安装 Debug APK
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/debug/app-debug.apk

# 清空应用数据（从全新登录页 / 新状态开始测试）
adb -s 127.0.0.1:16384 shell pm clear com.gugachat.app

# 强停并重启主 Activity
adb -s 127.0.0.1:16384 shell am force-stop com.gugachat.app
adb -s 127.0.0.1:16384 shell am start -n com.gugachat.app/.MainActivity
```

### UI 树获取与交互操作

1. **导出并解析当前界面控件坐标（`uiautomator dump`）**：
   ```bash
   adb -s 127.0.0.1:16384 shell uiautomator dump /sdcard/window_dump.xml
   adb -s 127.0.0.1:16384 pull /sdcard/window_dump.xml /tmp/window_dump.xml
   ```
   - 解析 `bounds="[x1,y1][x2,y2]"` 中心点 `((x1 + x2) / 2, (y1 + y2) / 2)` 后执行点击：
     ```bash
     adb -s 127.0.0.1:16384 shell input tap <x> <y>
     ```
2. **滑动与画布涂抹测试**（如对话滚动、Inpaint 局部重绘涂抹）：
   ```bash
   adb -s 127.0.0.1:16384 shell input swipe <x1> <y1> <x2> <y2> <duration_ms>
   ```
3. **文本输入与预填内容清空**：
   - Compose 输入框已有默认值（如默认 `Base URL`）时，先点击聚焦，移动到末尾并批量退格清空后再输入新文本：
     ```bash
     adb -s 127.0.0.1:16384 shell input keyevent 123
     adb -s 127.0.0.1:16384 shell input keyevent 67 67 67 67 67 67 67 67 67 67 67 67 67 67 67 67
     adb -s 127.0.0.1:16384 shell input text "http://127.0.0.1:18080/v1"
     ```
   - 空格需转义为 `%s`（仅建议用于 ASCII 字符）；收起软键盘前先检查键盘是否弹出，避免误关弹窗或返回桌面：
     ```bash
     adb -s 127.0.0.1:16384 shell dumpsys input_method | grep mInputShown=true && \
       adb -s 127.0.0.1:16384 shell input keyevent 4
     ```

### 截图保存与崩溃日志检查

```bash
# 清空旧日志（测试启动前执行）
adb -s 127.0.0.1:16384 logcat -c

# 截图并拉取到仓库外目录，再用视觉/图像工具核验界面布局、多语言与主题
adb -s 127.0.0.1:16384 shell screencap -p /sdcard/mumu_shot.png
adb -s 127.0.0.1:16384 pull /sdcard/mumu_shot.png /tmp/mumu_shot.png

# 测试结束后检查是否有未捕获异常崩溃
adb -s 127.0.0.1:16384 logcat -d | grep -iE "FATAL EXCEPTION" || echo "NO_FATAL_EXCEPTIONS"
```

---

## 4. 常见坑点与自动化注意事项

1. **Python `xml.etree.ElementTree` 空元素布尔值陷阱**：无子节点的 `<node ... />` 在布尔上下文中为 `False`，严禁写 `find(text=...) or find(desc=...)`；务必显式判断 `if node is None:`。
2. **底部导航精确匹配**：底部 Tab（`对话` / `作品` / `灵感` / `工具` / `我的`）请优先使用**精确文本匹配**（`node.text == "对话"`），避免误点页面正文中的 `"对话与记忆本地备份"` 等包含子串的控件。
3. **测试产物隔离与清理**：临时 Mock 服务端脚本、UI XML 转储文件与测试截图请统一写入仓库外部（如 Scratch 目录或 `/tmp`），严禁留在 `/Users/boxesp/code/gugachat` 内；测试完毕后需关闭后台 Mock 进程并执行 `adb -s 127.0.0.1:16384 reverse --remove-all`。
4. **多协议联调验证重点**：
   - 修改 `DirectProtocolCodec` / `DirectApiClient` / `ApiProviderStore` / `OnlineChatApi` / `OnlineImagesApi` 后，除运行 JVM `MockWebServer` 单测外，建议用 MuMu 验证：
     * 登录页「自定义 API 直连」测通并进入
     * **OpenAI Chat / Responses / Claude / Gemini** 4 协议切换与流式回复
     * 直连生图写入 `DirectImageStore` 并在「作品」画廊及详情页正确显示
