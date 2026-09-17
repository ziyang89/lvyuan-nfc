# 绿源解滴滴声 · NFC 写卡工具

安卓 NFC 写卡 App，内置一份固定的 MIFARE Classic 1K 卡数据（UID: `ACD45F61`）。

打开 App，把 CUID / UID 魔法空白卡贴到手机背面，App 会自动把全部 16 个扇区（含 UID 块）完整写入卡中。写好的卡拿去刷绿源车，即可解除超速"滴滴声"。

## 使用要求

- 手机必须带 NFC
- 写卡目标必须是 **CUID 或 UID 魔法卡**（普通白卡改不了 UID 块，0 扇区会写失败）
- ⚠️ 写卡会覆盖卡上原有全部数据，不要拿原装卡来写

## 使用步骤

① 手机打开 NFC 开关
② 打开本 App
③ 把卡贴到手机背面 NFC 区域，等待写入完成
④ 界面逐扇区显示结果，全部完成后即可拿去刷车

## 写卡逻辑

1. 先写 0 扇区 0 块（UID 块）：
   - CUID 卡：用默认密钥直接写
   - Gen1a 中国魔法卡：先发后门解锁指令（0x40/0x43）再写
2. 再遍历 16 个扇区：依次尝试 dump 里的 KeyA / KeyB / 出厂默认密钥认证，逐块写入

## 文件说明

- `app/src/main/java/com/donggua/lvyuannfc/MainActivity.kt` — 全部写卡逻辑
- `app/src/main/res/raw/dump.mct` — 内置的固定卡数据

## 兼容性

- Android 5.0（API 21）及以上
- 无第三方依赖，纯系统 NFC API（MifareClassic）
