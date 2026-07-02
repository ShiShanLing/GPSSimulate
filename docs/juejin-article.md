# 自己动手写了一个 Android 虚拟定位 App：GPSSimulate 技术实战

> 本文介绍我出于实际需求，从零开发的一款 Android 虚拟定位应用 **GPSSimulate**。文章涵盖项目起因、技术选型、核心实现思路，以及开发过程中踩过的坑。

---

## 写在前面

这篇文章以 **技术学习与个人开发实践** 为目的，分享 Android 官方模拟定位机制的实现方式。

请合理、合法地使用定位相关能力，尊重目标应用的服务条款。部分 App 会主动检测并拒绝模拟定位，文中也会如实说明。

---

## 一、起因：我想「假装」自己在另一个城市

前段时间有个很实际的需求。

我平时会关注一些换电/租车类 App，比如 **智租**。这类 App 的很多信息是 **按城市/地理位置** 展示的——月租套餐多少钱、站点覆盖、电池型号（48V / 60V 等）都会因城市不同而变化。

人没到现场，却想提前了解 **「如果我在苏州，能看到什么套餐？」** 或者 **「杭州和苏州的电池类型有什么区别？」**

市面上的虚拟定位 App 不少，但要么广告多，要么权限索取激进，要么在小米手机上不稳定。作为一个 Android 开发者，我索性自己写一个：

> **GPSSimulate** —— 地图选点 + 快捷城市定位 + 系统级 GPS 模拟。

核心目标很简单：

1. 在地图上拖动选点，直观地把位置改到目标城市
2. 支持保存常去城市（如苏州），一键跳转
3. 使用 Android **官方支持的模拟定位机制**，稳定、可维护

---

## 二、技术栈一览

| 类别 | 技术 | 说明 |
|------|------|------|
| 语言 | Kotlin | 项目主语言 |
| UI | Jetpack Compose + Material 3 | 声明式 UI，开发效率高 |
| 地图 | OSMDroid + OpenStreetMap | 免费、无需申请地图 API Key |
| 定位 | Google Play Services Location | 获取真实 GPS，用于地图初始定位 |
| 模拟定位 | `LocationManager` Test Provider | Android 官方模拟定位 API |
| 后台 | Foreground Service | 持续注入坐标，防止被系统杀掉 |
| 持久化 | SharedPreferences | 保存用户自定义的快捷城市 |
| 构建 | Gradle Kotlin DSL + AGP 9.x | Release 签名打包 |

**项目环境：**

- `minSdk = 30`（Android 11+）
- `targetSdk = 36`
- `compileSdk = 36`

---

## 三、虚拟定位的原理：不是黑科技，是官方 API

很多同类型 App 的核心原理其实一样：**向系统注册 Test Provider，注入假坐标**。

```kotlin
locationManager.addTestProvider(
    LocationManager.GPS_PROVIDER,
    /* requiresNetwork */ false,
    /* requiresSatellite */ false,
    /* requiresCell */ false,
    /* hasMonetaryCost */ false,
    /* supportsAltitude */ true,
    /* supportsSpeed */ true,
    /* supportsBearing */ true,
    Criteria.POWER_LOW,
    Criteria.ACCURACY_FINE,
)
locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
```

### 前置条件

用户必须在 **开发者选项** 中，将本 App 设为 **「模拟位置信息应用」**。

此外，Manifest 中需要声明（否则不会出现在系统候选列表里）：

```xml
<uses-permission
    android:name="android.permission.ACCESS_MOCK_LOCATION"
    tools:ignore="ProtectedPermissions" />
```

### 我踩过的第一个坑：只 mock GPS 不够

最初只注入了 `GPS_PROVIDER`，结果发现部分 App 读到的仍是真实位置。

原因是很多 App 使用 **融合定位（Fused Location）** 或 **网络定位（NETWORK_PROVIDER）**，并不只信 GPS。

**改进方案：同时 mock GPS + 网络定位，并每秒持续推送坐标。**

```kotlin
// 同时向 GPS 和 NETWORK 注入相同坐标
pushToProvider(LocationManager.GPS_PROVIDER, lat, lng, accuracy = 1f)
pushToProvider(LocationManager.NETWORK_PROVIDER, lat, lng, accuracy = 10f)
```

前台服务中每 1 秒 `tick()` 一次，确保依赖 `LocationListener` 的 App 能持续收到更新。

---

## 四、项目架构

```
app/
├── MainActivity.kt                 # 入口
├── ui/
│   └── LocationScreen.kt           # 地图 + 控制面板（Compose）
├── location/
│   ├── MockLocationProvider.kt     # 核心：注入模拟坐标
│   ├── MockLocationChecker.kt      # 检测是否已设为模拟定位 App
│   ├── LocationHelper.kt           # 获取真实定位
│   ├── PresetLocation.kt           # 快捷城市数据模型 + 解析
│   └── PresetLocationRepository.kt # 本地持久化
└── service/
    └── MockLocationService.kt      # 前台服务，持续模拟
```

---

## 五、核心模块拆解

### 1. 地图选点：中心图钉 + 拖动地图

交互参考了打车 App 的常见做法：

- 图钉固定在屏幕中心
- 用户拖动地图，中心点经纬度即为模拟目标
- 停止拖动 300ms 后更新坐标（防抖）

地图使用 **OSMDroid** 加载 OpenStreetMap 瓦片：

- ✅ 免费，无需 Key
- ✅ 瓦片自动磁盘缓存，看过区域二次打开更快
- ⚠️ 国内首次加载可能偏慢（服务器在国外）

```kotlin
MapView(ctx).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    controller.setZoom(16.0)
}
```

### 2. 快捷定位：预设城市 + 手动添加

内置默认城市「苏州」，并支持用户手动输入：

```
苏州, 31.3167, 120.6167
```

格式：`城市, 纬度, 经度`，逗号分隔，空格自动 trim。

数据通过 `SharedPreferences` 持久化，重启 App 不丢失。

### 3. 首次启动引导

若用户未在开发者选项中配置模拟定位 App，会弹出 **不可关闭的居中对话框**，引导前往设置，设置完成后自动消失。

### 4. 前台服务保活

模拟定位需要长时间运行时，使用 `Foreground Service` + 通知栏常驻，避免进程被系统回收：

```kotlin
class MockLocationService : Service() {
  // 每 1 秒 tick，持续注入坐标
}
```

---

## 六、开发过程中踩过的坑

### 1. 小米手机的 USB 安装限制

```
INSTALL_FAILED_USER_RESTRICTED: Installation via USB is disabled
```

解决：开发者选项 → 打开 **USB 安装** + **USB 调试（安全设置）**。

### 2. 真机定位失败

`getCurrentLocation()` 冷启动经常返回 `null`，地图默认跳到北京。

解决：定位策略改为 `lastLocation` → `getCurrentLocation` → `requestLocationUpdates` 三级回退，并等 `MapView` 创建完成后再定位。

### 3. Release 打包 Lint 报错

```
MockLocation: Mock locations should only be requested in a debug manifest
```

本 App 本身就是模拟定位工具，因此在 `build.gradle.kts` 中关闭了该 Lint 检查：

```kotlin
lint {
    disable += "MockLocation"
}
```

### 4. 哪些 App 仍然「骗不过」？

| 类型 | 结果 |
|------|------|
| 普通地图、天气、部分信息类 App | ✅ 通常有效 |
| 使用融合定位的 App | ⚠️ 部分有效 |
| 银行、支付、打车、游戏 | ❌ 多会检测 `isFromMockProvider()` |

> 回到智租这个场景：能否成功取决于它自身的定位策略和风控。本项目的价值更多在于 **理解 Android 定位机制 + 自用工具**，不保证能绕过所有商业 App 的检测。

---

## 七、使用流程（简版）

1. **开启开发者模式**：设置 → 关于手机 → 连点版本号 7 次
2. **设置模拟定位 App**：开发者选项 → 选择模拟位置信息应用 → GPSSimulate
3. 打开 App，授予位置权限
4. 拖动地图选点，或从右上角书签选择快捷城市
5. 点击 **「开始模拟」**
6. 打开目标 App 查看效果

---

## 八、总结与收获

这个项目源于一个很生活化的需求：**想在异地，提前看到当地 App 展示的信息**。

从技术角度看，最大的收获是：

1. 理解了 Android **Test Provider** 官方模拟定位机制
2. 认识到 **GPS / 网络 / 融合定位** 的差异，以及为什么要多 Provider 注入
3. 用 **Compose + OSMDroid** 快速搭出可用的地图交互
4. 踩遍了小米真机开发的各种权限坑

如果你也想做类似工具，不必追求「绕过一切风控」——把 **官方 API 用对、用稳**，已经能解决不少场景。

---

## 附录：本地打包

```bash
./scripts/build-release.sh
# 输出：app/build/outputs/apk/release/app-release.apk
```

---

**标签建议：** `Android` `Kotlin` `Jetpack Compose` `GPS` `虚拟定位`

**封面一句话：** 出于想跨城查看智租套餐的需求，我用 Kotlin + Compose 写了一个 Android 虚拟定位 App，本文分享技术方案与踩坑记录。

---

> 如果这篇文章对你有帮助，欢迎点赞收藏。有问题可以在评论区交流，我会尽量回复。
