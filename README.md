# 绿源解滴滴声 · NFC 写卡工具

## 这是什么

一个安卓 NFC 写卡 App，内置一份固定的 MIFARE Classic 1K 卡数据
（UID: `ACD45F61`，即绿源车解除超速"滴滴声"用的卡数据）。

打开 App 后，把 **CUID / UID 魔法空白卡** 贴到手机背面，
App 会自动把全部 16 个扇区（含 UID 块）完整写入卡中。
写好的卡拿去刷绿源车，即可解除滴滴声。

## 怎么打包 APK

### 方式一：GitHub Actions 云端打包（不用装任何软件）

工程已内置 `.github/workflows/build.yml`，推送到 GitHub 后自动打包：

1. 注册/登录 [github.com](https://github.com)
2. 右上角 `+` → `New repository`，名字随意（如 `lvyuan-nfc`），选 Public，点 Create
3. 进入仓库页面 → `uploading an existing file`（或 Add file → Upload files）
4. 把本文件夹里的所有内容（含 `.github` 文件夹）拖进去 → 点 `Commit changes`
5. 顶部 `Actions` 标签 → 选中 `Build APK` 那次运行 → 等几分钟变绿 ✓
6. 运行详情页底部 `Artifacts` → 下载 `lvyuan-nfc-apk`，解压得到 app-debug.apk
7. 传到手机安装（需允许"安装未知来源应用"）

> Artifacts 下载需要登录 GitHub；仓库选 Public 可无限免费使用 Actions。

### 方式二：本地 Android Studio 打包

1. 安装 [Android Studio](https://developer.android.com/studio)（自带 JDK，一路默认安装即可）
2. 打开 Android Studio → `Open` → 选择本文件夹 `LvYuanNfc`
3. 首次打开会自动下载 Gradle 和依赖，等右下角进度条跑完
4. 菜单 `Build` → `Build App Bundle(s) / APK(s)` → `Build APK(s)`
5. 打包完成后点右下角提示里的 `locate`，得到 APK：
   `app/build/outputs/apk/debug/app-debug.apk`
6. 把 APK 传到手机安装（需允许"安装未知来源应用"）

## 使用要求

- 手机必须带 NFC
- 写卡目标必须是 **CUID 或 UID 魔法卡**（普通白卡改不了 UID 块，0 扇区会写失败）
- ⚠️ 写卡会覆盖卡上原有全部数据，不要拿原装卡来写

## 工程结构

- `app/src/main/java/com/donggua/lvyuannfc/MainActivity.kt` — 全部写卡逻辑
- `app/src/main/res/raw/dump.mct` — 内置的固定卡数据（来自你发的文件）
- 依赖：无第三方库，纯系统 NFC API（MifareClassic）
- 兼容：Android 5.0（API 21）及以上

## 写卡逻辑说明

1. 先写 0 扇区 0 块（UID 块）：
   - CUID 卡：用默认密钥直接写
   - Gen1a 中国魔法卡：先发后门解锁指令（0x40/0x43）再写
2. 再遍历 16 个扇区：依次尝试 dump 里的 KeyA / KeyB / 出厂默认密钥认证，逐块写入
3. 界面会逐扇区显示写入结果，哪个扇区失败一目了然
