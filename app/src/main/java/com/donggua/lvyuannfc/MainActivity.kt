package com.donggua.lvyuannfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private var nfcAdapter: NfcAdapter? = null
    @Volatile
    private var busy = false

    // 出厂默认密钥 FFFFFFFFFFFFFF
    private val defaultKey = byteArrayOf(
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this)
        status.textSize = 17f
        status.setPadding(60, 120, 60, 60)
        setContentView(status)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            status.text = "此手机不支持 NFC，无法写卡。"
        } else {
            showIdle()
        }
    }

    private fun showIdle() {
        status.text = "绿源 · 解除滴滴声 写卡工具\n\n" +
                "① 手机打开 NFC 开关\n" +
                "② 把 CUID / UID 魔法空白卡贴到手机背面 NFC 区域\n\n" +
                "卡贴上后会自动写入固定数据（UID: ACD45F61…），\n" +
                "写完的卡拿去刷绿源车即可解除超速滴滴声。\n\n" +
                "⚠️ 注意：\n" +
                "· 必须用空白魔法卡（CUID/UID 卡），普通白卡写不了 UID 块\n" +
                "· 卡上会写 16 个扇区共 64 块，原有数据会被覆盖\n" +
                "· 一次只放一张卡，写完拿开再放下一张"
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        adapter.enableForegroundDispatch(
            this, pi,
            arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)),
            null
        )
    }

    override fun onPause() {
        super.onPause()
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (busy) return
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
            busy = true
            status.text = "已检测到卡片，正在写入…\n\n请不要移开卡片，直到显示完成。"
            Thread {
                val result = try {
                    writeCard(tag)
                } catch (e: Exception) {
                    "❌ 写卡出错：${e.message ?: e.javaClass.simpleName}\n\n请把卡拿开重新贴上重试。"
                }
                runOnUiThread {
                    status.text = result
                    busy = false
                }
            }.start()
        }
    }

    // ---------- 写卡主流程 ----------

    private fun writeCard(tag: Tag): String {
        val dump = parseDump()
        val mfc = MifareClassic.get(tag)
            ?: return "❌ 不是 MIFARE Classic 卡。\n\n请使用 CUID / UID 魔法空白卡（1K）。"
        val log = StringBuilder()
        try {
            mfc.connect()
            if (mfc.size != MifareClassic.SIZE_1K) {
                log.append("⚠️ 卡容量 ${mfc.size} 字节，不是标准 1K 卡，结果可能不完整\n\n")
            }

            // 1. 先写 0 扇区 0 块（UID 块，需要魔法卡）
            val okUid = writeBlock0(mfc, dump[0][0], log)
            if (!okUid) {
                log.insert(0, "❌ UID 块（0 扇区 0 块）写入失败：\n" +
                        "这张卡不支持改 UID，需要 CUID / UID 魔法卡。\n\n")
            }

            // 2. 写全部 16 个扇区
            for (s in 0..15) {
                val firstBlock = mfc.sectorToBlock(s)
                if (!authSector(mfc, s, dump)) {
                    log.append("扇区 $s：认证失败，已跳过\n")
                    continue
                }
                var sectorOk = true
                for (b in 0..3) {
                    if (s == 0 && b == 0) continue // UID 块已单独处理
                    val blockIdx = firstBlock + b
                    try {
                        mfc.writeBlock(blockIdx, dump[s][b])
                    } catch (e: Exception) {
                        sectorOk = false
                        log.append("扇区 $s 块 $b：写入失败（${e.javaClass.simpleName}）\n")
                    }
                }
                if (sectorOk) log.append("扇区 $s：✓ 完成\n")
            }
            log.insert(0, if (okUid) "✅ 写入完成！\n\n" else "")
            log.append("\n已写数据：UID ACD45F61 全卡克隆\n可以把卡拿去刷车了。")
            return log.toString()
        } catch (e: Exception) {
            return "❌ 写卡出错：${e.message ?: e.javaClass.simpleName}\n\n请把卡拿开重新贴上重试。\n\n$log"
        } finally {
            try {
                mfc.close()
            } catch (_: Exception) {
            }
        }
    }

    /** 写 UID 块。先试普通写（CUID 卡），失败再试 Gen1a 中国魔法卡解锁。 */
    private fun writeBlock0(mfc: MifareClassic, block: ByteArray, log: StringBuilder): Boolean {
        // 路径 1：CUID 卡直接写
        try {
            if (mfc.authenticateSectorWithKeyA(0, defaultKey)) {
                mfc.writeBlock(0, block)
                log.append("0 扇区 0 块（UID）：✓ 写入成功\n")
                return true
            }
        } catch (_: Exception) {
        }
        // 路径 2：Gen1a（中国魔法卡）后门解锁
        try {
            val r1 = mfc.transceive(byteArrayOf(0x40, 0x01))
            if (r1 != null && r1.isNotEmpty() && r1[0] == 0x0A.toByte()) {
                val r2 = mfc.transceive(byteArrayOf(0x43))
                if (r2 != null && r2.isNotEmpty() && r2[0] == 0x0A.toByte()) {
                    mfc.authenticateSectorWithKeyA(0, defaultKey)
                    mfc.writeBlock(0, block)
                    log.append("0 扇区 0 块（UID）：✓ 写入成功（Gen1a 魔法卡解锁）\n")
                    return true
                }
            }
        } catch (_: Exception) {
        }
        return false
    }

    /** 依次尝试 dump 里的 KeyA / KeyB / 出厂默认密钥 */
    private fun authSector(mfc: MifareClassic, sector: Int, dump: Array<Array<ByteArray>>): Boolean {
        val trailer = dump[sector][3]
        val keyA = trailer.copyOfRange(0, 6)
        val keyB = trailer.copyOfRange(10, 16)
        for (key in arrayOf(keyA, keyB, defaultKey)) {
            try {
                if (mfc.authenticateSectorWithKeyA(sector, key)) return true
                if (mfc.authenticateSectorWithKeyB(sector, key)) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    // ---------- 解析 dump.mct ----------

    /** 解析 res/raw/dump.mct，返回 16 扇区 × 4 块的数据 */
    private fun parseDump(): Array<Array<ByteArray>> {
        val raw = resources.openRawResource(R.raw.dump).bufferedReader().use { it.readText() }
        val sectors = Array(16) { Array(4) { ByteArray(16) } }
        var s = -1
        var b = 0
        for (line in raw.lineSequence()) {
            val t = line.trim()
            when {
                t.startsWith("+Sector:") -> {
                    s = t.substringAfter(":").trim().toInt()
                    b = 0
                }
                s >= 0 && t.length == 32 && t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } -> {
                    if (b < 4) {
                        sectors[s][b] = hexToBytes(t)
                        b++
                    }
                }
            }
        }
        return sectors
    }

    private fun hexToBytes(s: String): ByteArray = ByteArray(16) { i ->
        ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte()
    }
}
