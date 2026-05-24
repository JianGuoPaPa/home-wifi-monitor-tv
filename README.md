# 家庭 Wi-Fi 电视看板 / Home Wi-Fi Monitor TV

一个运行在 Android TV 上的家庭 Wi-Fi 设备用网监控看板。电视端可以直接登录小米 / MiWiFi 兼容路由器，读取在线设备、实时速率、WAN 上传下载、Mesh 节点等信息；安装到电视后不依赖电脑或额外服务器。

This is a local-first Android TV dashboard for monitoring home Wi-Fi device traffic on Xiaomi/MiWiFi-compatible routers. The TV app talks directly to the router, so no companion computer is required after installation.

## 运行效果

以下为实际运行照片。仓库中同时保留了 HEIC 原图，README 使用 JPG 版本以便 GitHub 页面稳定预览。

<p align="center">
  <img src="docs/images/demo.jpg" alt="家庭 Wi-Fi 电视看板运行演示" width="760">
</p>

<p align="center">
  <img src="docs/images/router-settings.jpg" alt="路由器设置界面" width="520">
</p>

## 主要功能

- Android TV 横屏大屏界面，适合电视遥控器和方向键操作。
- 每秒刷新看板数据，同时对较慢的路由器接口做缓存，减少对路由器管理页的压力。
- 展示 WAN 下载 / 上传、在线设备、活跃设备、Mesh 节点和设备速率排行。
- 首次启动弹出路由器设置，路由器地址和管理密码只保存在电视本机。
- 支持设备筛选：全部、正在用网、高流量、Mesh 节点。
- 仓库内附带一个可选的 Node.js 浏览器看板，便于本地调试和验证路由器解析逻辑。

## 项目结构

```text
android-tv/       Android TV 应用源码
web-dashboard/    可选的 Node.js 浏览器看板和解析测试
docs/images/      README 使用的演示图片
LICENSE           MIT 许可证
README.md         中英双语说明文档
```

## Android TV 应用

环境要求：

- Android Studio 或可用的 Android SDK
- JDK 17
- 与 Android Gradle Plugin 8.7.3 兼容的 Gradle

调试构建：

```bash
cd android-tv
gradle assembleDebug
```

发布构建：

```bash
cd android-tv
gradle assembleRelease
```

首次启动时，在设置弹窗中输入路由器管理地址和管理密码。路由器地址可以是电视所在家庭网络可访问的本地域名或地址。

遥控器快捷操作：

- 方向键：在筛选按钮、刷新 / 设置按钮、设备列表之间移动
- Menu 或 Settings 键：打开路由器设置
- Refresh 键或 `R`：立即刷新
- Channel/Page 键：滚动设备列表

## 浏览器调试看板

浏览器看板是可选模块，主要用于开发机上调试路由器数据解析和 UI 思路。

```bash
cd web-dashboard
npm test
cp .env.example .env
```

编辑 `.env`，填入你的路由器管理地址和管理密码，然后启动：

```bash
npm start
```

默认只监听 `localhost`。如果要暴露到家庭网络中的其他设备，请自行设置 `BIND_HOST`，并注意这个调试看板没有内置登录鉴权。

## 配置说明

`web-dashboard/.env.example` 中列出了浏览器调试看板支持的本地环境变量：

- `ROUTER_HOST`：运行调试看板的机器可访问的路由器管理地址
- `ROUTER_PASSWORD`：路由器管理密码
- `PORT`：浏览器看板端口
- `BIND_HOST`：浏览器看板监听地址
- `SNAPSHOT_TTL_MS`：浏览器看板快照短缓存时间

Android TV 应用通过电视端设置弹窗保存配置，不需要提交任何本地配置文件。

## 隐私与安全

这个项目面向可信任的家庭局域网使用。

- 应用不包含遥测、统计、云同步或外部 API 调用。
- 源码不硬编码路由器地址、管理密码、设备名称、内网 IP 或 MAC 地址。
- APK、本地环境文件、签名密钥、`local.properties` 等不会进入 Git。
- 运行时读取的路由器设备列表可能包含设备名称、IP 和 MAC。公开你自己的截图或日志前，请先确认是否需要脱敏。
- 浏览器调试看板没有内置鉴权，默认只建议绑定在 `localhost`。

## English

Home Wi-Fi Monitor TV is a local-first Android TV dashboard for monitoring device traffic on Xiaomi/MiWiFi-compatible routers.

Highlights:

- Designed for landscape Android TV screens and remote-control navigation.
- Refreshes the dashboard every second while caching slower router calls.
- Shows WAN upload/download, online device count, active device count, Mesh nodes, and per-device speed rows.
- Stores router settings only on the TV device.
- Includes an optional Node.js browser dashboard for local development and parser testing.

Build the Android TV app:

```bash
cd android-tv
gradle assembleDebug
```

Run the optional browser dashboard:

```bash
cd web-dashboard
npm test
cp .env.example .env
npm start
```

Keep this project on a trusted home network. Before publishing your own runtime logs or screenshots, decide whether you need to remove device names, local addresses, MAC addresses, and router identifiers.

## 许可证 / License

MIT

## 技术交流 / Contact

如果你对 Android TV、家庭网络监控、小米路由器本地接口或这个项目的二次开发感兴趣，可以扫码添加微信做技术交流。

<p align="center">
  <img src="docs/images/wechat-qr.jpg" alt="微信二维码" width="260">
</p>
