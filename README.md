# 🚗 FuelTracker（随记油耗）

一款纯本地、无广告、极简的 Android 车辆油耗与记账应用。基于 Kotlin 和 Jetpack Compose 开发。

<!-- 如果有 Release 包，取消下面这行的注释并修改链接 -->
<!-- [![Download APK](https://img.shields.io/badge/Download-APK-success.svg)](https://github.com/furenkun123/FuelTracker/releases) -->

<!-- 📸 强烈建议在这里放上你的应用截图，会让项目好看 10 倍 -->
<!--
<p align="center">
  <img src=https://github.com/user-attachments/assets/c217f8fb-eadf-4393-befc-a1ca97206981" width="200" />
  <img src="https://github.com/user-attachments/assets/acf1b8e3-fbd8-4919-965f-d5b8e645c92b" width="200" />
  <img src="https://github.com/user-attachments/assets/ce3e5352-d449-4179-bf7f-1123b73fbfc1" width="200" />
</p>
-->

## ✨ 核心功能

- **⛽ 高精度油耗计算**：基于满箱法计算精准油耗。内置离群检测与防错逻辑，自动过滤异常里程、漏记数据，不怕误录。
- **🚙 多车与日常记账**：支持多辆车管理，一键切换。支持记录加油（含漏记折算）与各类费用（停车、保养、高速等）。
- **🅿️ 智慧停车**：记录车位与位置，首页常驻卡片提醒，支持一键取车与通知。
- **🔄 数据迁移与备份**：支持导出/导入本地 JSON 备份。支持一键导入“小熊油耗”导出的 CSV 数据，自动去重并过滤异常。
- **🎨 现代化 UI**：类 MIUI 风格的圆角卡片设计，支持深色模式，动画丝滑流畅。

## 🛠️ 技术栈

- 100% Kotlin + Jetpack Compose
- MVVM 架构（ViewModel + StateFlow）
- Room 数据库（本地存储）
- 协程（Coroutines）
- 部分组件使用了 [Kyant Backdrop](https://github.com/Kyant0/Backdrop) 实现液态玻璃效果

## 🚗 车辆数据库说明

项目内置车辆数据库（`assets/car_brands.json`），数据来源于第三方公开资料，经整理、清洗后用于车型查询，**仅供学习与交流使用**。

本项目不代表数据来源方的官方授权、认可或合作。若相关内容涉及侵权，请联系作者确认后将立即删除。

## 🔒 隐私安全

- **完全离线**：应用不包含任何网络请求代码。
- **数据自主**：所有加油、费用、车辆数据均保存在您手机本地的数据库中，不会上传服务器。

## 🚀 如何构建

1. 确保你的开发环境使用 **JDK 17**。
2. 使用 Android Studio 打开项目。
3. 等待 Gradle 同步完成。
4. 直接 Run 即可安装到手机。

---

## 🙏 开源致谢

FuelTracker 使用了以下优秀的开源项目，感谢开发者和社区的贡献：

- [Kotlin](https://github.com/JetBrains/kotlin)
- [Jetpack Compose](https://developer.android.com/compose)
- [AndroidX](https://developer.android.com/jetpack/androidx)
- [Room](https://developer.android.com/training/data-storage/room)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kyant Backdrop](https://github.com/Kyant0/Backdrop)
- [Kyant Shapes](https://github.com/Kyant0/Shapes)

感谢所有开源项目的开发者和维护者。

---

## 📄 开源协议

本项目**源代码**采用 [Apache License 2.0](LICENSE) 开源。
第三方依赖及车辆数据库等外部资料**不在**本项目源代码许可范围内，分别遵循其原有许可或相关使用规定。

Copyright 2026 furenkun123