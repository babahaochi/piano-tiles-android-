# Piano Tiles（安卓平台钢琴块）

一个轻量、响应灵敏的钢琴块游戏。支持中英文、沉浸式全屏、片段化动效、节拍模式、本地统计与排行榜，适合离线游玩。

## 主要特性
- 流畅的 60 FPS 渲染循环（SurfaceView）
- 正确的判定与得分：Perfect/Great/Good/Miss；误触只断连击，不会直接游戏结束
- 物体池与加锁保证稳定，生命周期与线程安全处理
- 动效：命中波纹、连击脉冲 HUD、轻微 Miss 震屏、下落轨迹与柔和命中闪光
- 声音：点击按轨道播放钢琴音色（运行时生成 WAV + SoundPool），可选柔和背景铺底
- 菜单配置：轨道数（3/4/5）、难度（Easy/Normal/Hard）或节拍模式（BPM）、主题、震动、声音、可复现图案种子
- HUD 信息条：实时显示轨道/难度/语言
- UI 视觉：渐变背景、圆角按钮/卡片、暗色下拉、芯片式单选
- 沉浸式系统栏：透明状态/导航栏并自动隐藏，边到边显示
- 本地统计与排行榜：仅存储在设备 SharedPreferences，无任何联网

## 游戏玩法
- 点击屏幕底部判定线附近的黑色钢琴块以得分并累计连击
- 漏接并落出屏幕才算游戏结束；误触仅清空连击并给出 Miss 反馈
- 顶部暂停按钮可随时暂停/恢复

## 菜单配置项说明
- 轨道数：3 / 4 / 5
- 难度：Easy / Normal / Hard（或启用“节拍模式”并设置 BPM）
- 震动/声音：命中回馈
- 主题：跟随系统/浅色/深色
- 语言：中文/English（进入即生效并持久化）
- 可选：使用固定种子生成可复现实验图案

## 多语言与沉浸式
- 语言在菜单页切换，立即生效；游戏 HUD 同步展示当前语言
- 全局采用 edge-to-edge，菜单/统计页自动避让系统栏安全区；游戏页为真正全屏触控

## 构建与运行
推荐使用 Android Studio（自动处理 Gradle 与签名配置）：
1) File > Open 选择项目根目录
2) 连接设备或启动模拟器
3) 点击 Run 运行

命令行（Windows PowerShell）：
```powershell
# 若仓库缺少 wrapper，先用已安装的 Gradle 生成（需本机已安装 Gradle）
gradle wrapper --gradle-version 8.9

# 调试包
./gradlew.bat --no-daemon assembleDebug
```
APK 输出：`app/build/outputs/apk/debug/`

发布签名：在 Android Studio 使用 Build > Generate Signed Bundle / APK 按向导生成。

## 常见问题（FAQ）
1) Gradle wrapper jar file not found
- 原因：仓库缺少 wrapper jar
- 处理：执行上文“生成 wrapper”命令，或使用 Android Studio 打开工程让其自动修复

2) Android 资源链接失败（AAPT）
- 多数为资源引用名错误或缺失，请对照报错检查 colors/drawables/strings 的引用是否存在

3) 设备上无声/卡顿
- 某些设备对 SoundPool 同时音轨数较敏感，建议关闭后台铺底或降低系统音量渐进测试

4) 沉浸式与手势冲突
- 系统栏可通过上滑临时唤出；菜单/统计已自动避让；如有遮挡现象请反馈具体设备与系统版本

## 目录结构（简要）
```
├─ app/
│  ├─ build.gradle.kts
│  ├─ proguard-rules.pro
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/example/pianotiles/
│     │  ├─ MenuActivity.kt      # 菜单与配置
│     │  ├─ MainActivity.kt      # 游戏入口（包含暂停）
│     │  ├─ StatsActivity.kt     # 本地统计与排行榜
│     │  ├─ ui/GameView.kt       # 渲染线程与触控分发
│     │  ├─ game/GameEngine.kt   # 逻辑/渲染/判定/动效/HUD
│     │  └─ stats/StatsRepository.kt # 本地持久化
│     └─ res/                     # 渐变背景、芯片单选、深色下拉等资源
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradlew / gradlew.bat
└─ README.md
```

## 隐私
本应用不联网，所有数据仅保存在本地 SharedPreferences 中。

## 贡献
欢迎提交 Issue 与 PR。
