# Media Downloader App

原生 Android 媒体批量下载器 —— 在手机上直接解析并下载 Twitter/X、Bluesky、图片/GIF 等平台的媒体，**不经过任何服务器中转**。

## ✨ 功能

- 📋 **批量粘贴**：一次粘贴多个链接，自动识别拆分
  - 支持无分隔连写链接（`.../123https://...` 也能拆开）
  - 自动剔除中文 / 标点等尾部无关字符
  - 自动去重
- 🐦 **Twitter / X**：支持引用转推、多图、多视频，图片自动取高清（`name=large`）
- 📘 **Bluesky**：图片 / 视频全解析
- 🖼️ **图片 / GIF**：直链下载
- 📥 **直接下载**：解析后直接保存到系统「下载」目录，无需中转
- 🔌 **走本地代理**：下载走手机网络 + 自动读取手机系统代理（clash 等），TUN / 系统代理模式均生效

## 📦 安装

1. 从 [Releases](https://github.com/Zaienscookie/media-downloader-app/releases) 下载 `media-downloader-app-debug.apk`
2. 手机上允许「安装未知来源应用」
3. 点击 APK 安装
4. 打开应用，粘贴链接，点击「批量解析下载」

> 要求：Android 5.0+（minSdk 21），Android 10+ 无需存储权限。

## 🚀 使用

1. 在输入框中粘贴一个或多个链接（支持换行 / 空格 / 逗号 / 连写分隔）
2. 点击「批量解析下载」
3. 自动解析全部链接 → 逐个下载 → 结果以卡片展示

支持链接示例：

```
https://x.com/用户名/status/1234567890
https://bsky.app/profile/用户名/post/abc123xyz
https://example.com/pic.jpg
```

## 🛠 技术要点

- **纯原生 Android**（非 WebView 壳），直连 fxtwitter / Bluesky 公开 API 解析
- 网络层：`HttpURLConnection` 直连源站，显式读取系统代理（`ConnectivityManager.getDefaultProxy` 或 `http.proxyHost`）应用到连接
- 存储：`MediaStore.Downloads` 写入，多线程下载
- 无第三方依赖，APK 极小（约 27KB）

## 📁 项目结构

```
media-downloader-app/
├── app/
│   ├── build.gradle          # Android 构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/zaiens/mediadl/MainActivity.java   # 主程序（解析 + 下载）
├── build.gradle              # 根构建脚本
├── settings.gradle
├── gradle.properties
└── dist/                     # 构建产物 APK
```

## 🧱 本地构建

```bash
export ANDROID_HOME=/path/to/android-sdk
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 📄 License

MIT
