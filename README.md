# 家庭 Wi-Fi 电视看板

[中文](README.md) | [English](README.en.md)

一个运行在 Android TV 上的家庭 Wi-Fi 设备用网监控看板。电视端可以直接登录小米 / MiWiFi 兼容路由器，读取在线设备、实时速率、WAN 上传下载、Mesh 节点等信息；安装到电视后不依赖电脑或额外服务器。

适合想查看家里网络使用情况，但又没有使用软路由、OpenWrt、旁路由或专业网关的朋友。只要你的路由器本身能提供已连接设备和带宽数据，就可以把电视变成一个常驻的家庭网络监控面板。

## 运行效果

以下为实际运行照片。

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

## 关键词

小米路由器、MiWiFi、家庭 Wi-Fi、家庭wifi、家庭网络、网络监控、流量监控、带宽监控、监控面板、电视看板、Android TV、设备用网、局域网监控、无软路由、软路由替代参考。

## 适用前提与限制

这个软件是基于小米系路由器的本地管理接口做的，不是通用路由器监控协议。

使用前请确认：

- 你拥有这台路由器的管理账号和密码。
- 你的电视、路由器和运行调试看板的设备在同一个可信任的家庭局域网内。
- 你的路由器管理页确实能提供“当前已连接设备列表”和“设备实时上传 / 下载速率”等数据。
- 你的路由器接口返回的数据结构和小米 / MiWiFi 系列路由器兼容；不同型号和固件版本可能需要适配。

关于刷新频率：

- 看板界面会按秒刷新，但最终能看到多实时的数据，取决于路由器本身多久更新一次设备速率。
- 设备列表、Mesh 拓扑等较慢接口会做缓存，避免每秒反复请求导致路由器管理页变慢或路由器负载升高。
- 如果数据看起来不是每秒变化，通常是路由器接口本身的数据更新间隔或负载限制，而不是电视 App 单方面能解决的问题。

## AI 适配建议

虽然当前版本是基于小米系路由器做的，但它也可以作为“家庭路由器电视看板”的参考项目。你可以把这个仓库交给 AI，再提供你自己家的环境信息，让 AI 帮你快速改成可用版本。

适配时建议告诉 AI：

- 路由器品牌、型号和固件版本。
- 路由器管理页地址、登录方式，以及你能拿到的接口返回示例。
- 设备列表、设备名称、IP、MAC、上传速率、下载速率在接口里的字段位置。
- 目标电视或盒子的 Android 版本、屏幕分辨率和遥控器操作习惯。
- 你希望保留哪些界面元素，以及希望隐藏哪些隐私信息。

如果你的路由器不是小米系，通常需要重点改 `RouterClient` 里的登录、接口请求和数据解析逻辑；电视端 UI、筛选、刷新节奏等部分可以继续复用。

## 技术栈说明

这个仓库使用这些技术，是因为我这里做这个版本时选择了这套实现方式，不代表其他人必须使用完全相同的技术栈。

- Android TV 应用：原生 Android Java。
- 构建：Android Gradle Plugin 8.7.3，JDK 17。
- 本地调试看板：Node.js ES Modules。
- 测试：Node.js `node:test`，Android 单元测试。
- 架构：电视端直接访问路由器本地接口，不依赖云服务。

网友拿到这个仓库后，可以让 AI 根据自己的路由器、电视设备和开发习惯继续修改：例如改成 Kotlin、改 UI、换路由器接口、删除浏览器调试看板，或者加入更多本地统计能力。

项目代码主要由 AI 辅助生成，适合学习、二次开发和家庭自用。实际使用时请根据你的路由器型号、固件版本和网络环境自行测试。

## 项目结构

```text
android-tv/       Android TV 应用源码
web-dashboard/    可选的 Node.js 浏览器看板和解析测试
docs/images/      README 使用的演示图片
LICENSE           MIT 许可证
README.md         中文主入口
README.en.md      English README
```

## Android TV 应用

环境要求：

- Android Studio 或可用的 Android SDK
- JDK 17
- 与 Android Gradle Plugin 8.7.3 兼容的 Gradle

推荐用仓库里的脚本打包：

```bash
scripts/build-apk.sh release
```

打包完成后，APK 会输出到：

```text
dist/home-wifi-monitor-tv-release.apk
```

如果要调试包：

```bash
scripts/build-apk.sh debug
```

也可以直接进入 Android 项目执行 Gradle：

```bash
cd android-tv
gradle assembleDebug
gradle assembleRelease
```

如果没有 Android Studio，需要先准备 Android SDK，并设置 `ANDROID_HOME` / `ANDROID_SDK_ROOT`，或者在 `android-tv/local.properties` 中写入 `sdk.dir=/path/to/Android/sdk`。`local.properties` 不会提交到 Git。

当前仓库里的 release 构建为了方便家庭自用和电视侧载，使用 Android debug signing config 签名。正式分发前，建议换成你自己的 keystore。

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

## 许可证

MIT

## 技术交流

如果你对 Android TV、家庭网络监控、小米路由器本地接口或这个项目的二次开发感兴趣，可以扫码添加微信做技术交流。

<p align="center">
  <img src="docs/images/wechat-qr.jpg" alt="微信二维码" width="260">
</p>
