# Release and Promotion Checklist

这个清单用于发布 `home-wifi-monitor-tv` 的公开版本，并把仓库从“能打开”优化到“路人愿意点进来、愿意 Star、愿意尝试侧载 APK”。

## 1. 发布前检查

- README 第一屏能说明项目用途：Android TV 家庭 Wi-Fi 监控、小米 / MiWiFi 路由器、电视端独立运行、不依赖软路由。
- README 至少包含一张电视实际运行图和一张设置图。
- `web-dashboard/.env.example` 不包含真实路由器地址、密码、设备名、内网 IP 或 MAC。
- `android-tv/local.properties`、签名密钥、APK、AAB、截图原图和本地构建目录都没有进入 Git。
- Web 调试看板测试通过：`cd web-dashboard && npm test`。
- Android 单元测试通过：`cd android-tv && ./gradlew testDebugUnitTest`。
- 如发布 APK，说明当前签名方式；正式分发前建议使用自己的 release keystore。

## 2. 推荐 Release 内容

建议从 `v1.3.0` 或 `v0.1.0` 开始发正式 Release。若跟随 Android `versionName`，当前可用：

```text
v1.3.0 - Android TV 家庭 Wi-Fi 监控看板
```

Release 摘要：

```text
Android TV 家庭 Wi-Fi 设备用网监控看板，面向小米 / MiWiFi 兼容路由器。电视端安装后可独立读取在线设备、实时速率、WAN 上传下载、Mesh 节点和设备速率排行，不依赖软路由、OpenWrt、旁路由或额外电脑。
```

Release 亮点：

- Android TV 横屏大屏界面，支持遥控器方向键操作。
- 电视端独立访问路由器本地接口，不依赖外部服务器。
- 展示 WAN 上传 / 下载、在线设备、活跃设备、Mesh 节点和设备速率。
- 支持全部、正在用网、高流量、Mesh 节点筛选。
- 1 秒刷新看板数据，慢接口做缓存，减少路由器压力。
- 附带 Node.js 浏览器调试看板，便于验证路由器解析逻辑。

Release 附件建议：

- `home-wifi-monitor-tv-release.apk`
- `home-wifi-monitor-tv-debug.apk`，如需要
- 运行截图或演示 GIF

## 3. GitHub 仓库设置

推荐仓库描述：

```text
Android TV 家庭 Wi-Fi 监控看板：小米 / MiWiFi 路由器设备流量、WAN 上传下载、Mesh 节点和设备速率排行，不依赖软路由或额外服务器。
```

推荐 Topics：

```text
android-tv
android-tv-apk
xiaomi-router
miwifi
home-wifi
wifi-monitor
router-monitor
network-dashboard
bandwidth-monitor
lan-monitor
home-network
tv-dashboard
local-first
no-soft-router
java
```

## 4. 外部分发文案

### 短版

```text
整理了一个 Android TV 家庭 Wi-Fi 监控看板：面向小米 / MiWiFi 兼容路由器，电视端安装后可独立显示在线设备、实时速率、WAN 上传下载、Mesh 节点和设备排行。不需要软路由、OpenWrt、旁路由或电脑常驻。

GitHub: https://github.com/JianGuoPaPa/home-wifi-monitor-tv
```

### 长版

```text
我把自己折腾的家庭 Wi-Fi 电视看板整理成了开源项目。

它适合普通家庭网络：没有软路由、没有 OpenWrt、没有专业网关，但想在电视上常驻看谁在用网、哪个设备占带宽、WAN 上传下载是多少。

主要特点：
- Android TV / 电视盒子直接运行
- 支持小米 / MiWiFi 兼容路由器
- 不依赖电脑或额外服务器
- 1 秒刷新看板数据
- 支持电视遥控器方向键
- 展示在线设备、活跃设备、Mesh 节点、设备速率排行
- 附带 Node.js 浏览器调试看板，方便先验证路由器接口

GitHub: https://github.com/JianGuoPaPa/home-wifi-monitor-tv
```

## 5. 可发布的平台

- GitHub Release
- V2EX：分享创造 / Android / 宽带症候群相关讨论
- 掘金：Android TV、家庭网络、本地自动化方向
- 知乎：家庭网络监控、小米路由器、Android TV 应用方向
- 小红书：用电视运行照片 + “不用软路由也能看家庭设备用网”做轻量展示
- 闲鱼商品页：作为源码 / APK 学习项目展示，注意说明需要自行适配路由器型号

## 6. 后续更容易拿 Star 的改进

- 增加 GitHub Release，并附上可侧载 APK。
- 增加 20-30 秒电视实拍演示视频或 GIF。
- 增加 `docs/FAQ.md`：路由器适配、ADB 安装、密码保存、刷新间隔、数据不动的原因。
- 增加 `docs/ARCHITECTURE.md`：Android TV、RouterClient、浏览器调试看板和路由器接口的数据流。
- 增加更多路由器型号适配记录，例如小米 AX 系列、红米 AX 系列。
- 增加截图脱敏说明，避免公开设备 MAC、IP 和真实设备名。
