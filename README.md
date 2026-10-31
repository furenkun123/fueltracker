

# 🚗 FuelTracker (随记油耗)

一款纯本地、无广告、极简的 Android 车辆油耗与记账应用。基于 Kotlin 和 Jetpack Compose 开发。

## ✨ 核心功能

*   **⛽ 高精度油耗计算**：基于满箱法计算精准油耗。内置离群检测与防错逻辑，自动过滤异常里程、漏记数据，不怕误录。
*   **🚙 多车与日常记账**：支持多辆车管理，一键切换。支持记录加油（含漏记折算）与各类费用（停车、保养、高速等）。
*   **🅿️ 智慧停车**：记录车位与位置，首页常驻卡片提醒，支持一键取车与通知。
*   **🔄 数据迁移与备份**：支持导出/导入本地 JSON 备份。支持一键导入“小熊油耗”导出的 CSV 数据，自动去重并过滤异常。
*   **🎨 现代化 UI**：类 MIUI 风格的圆角卡片设计，支持深色模式，动画丝滑流畅。

## 🛠️ 技术栈

*   100% Kotlin + Jetpack Compose
*   MVVM 架构 (ViewModel + StateFlow)
*   Room 数据库 (本地存储)
*   协程 (Coroutines)

## 🔒 隐私安全

*   **完全离线**：应用不包含任何网络请求代码。
*   **数据自主**：所有加油、费用、车辆数据均保存在您手机本地的数据库中，绝不上传服务器。

## 🚀 如何构建

1. 使用 Android Studio 打开项目。
2. 等待 Gradle 同步完成。
3. 直接 Run 即可安装到手机。

---
*如果觉得好用，欢迎点个 ⭐ Star！*

## 开源协议（License）

FuelTracker 采用 **Apache License 2.0** 开源协议。

你可以在遵守 Apache License 2.0 协议条款的前提下，自由使用、复制、修改和分发本项目的源代码，包括用于商业用途。

使用、修改或分发本项目时，请保留原有版权声明、许可证文件及协议要求的相关声明。

完整协议内容请查看项目根目录下的 [LICENSE](LICENSE) 文件。

本项目使用的第三方开源依赖遵循各自的许可证协议。

---

FuelTracker is licensed under the Apache License, Version 2.0.

See the [LICENSE](LICENSE) file for the full license text.

Copyright 2026 furenkun123
