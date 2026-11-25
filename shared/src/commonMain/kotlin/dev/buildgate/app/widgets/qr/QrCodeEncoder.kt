package dev.buildgate.app.widgets.qr

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code encoder (Byte mode, Error Correction Level M, Versions 1..10).
 * Zero external dependencies — works identically across Android, WasmJS, and JS.
 */
object QrCodeEncoder {

    data class QrMatrix(val size: Int, val modules: BooleanArray) {
        fun get(row: Int, col: Int): Boolean = modules[row * size + col]
    }

    // Version 1..10 specs for Error Correction Level M (1-indexed via index = version - 1)
    // Triple(totalDataCodewords, ecCodewordsPerBlock, numBlocks)
    private val versionSpecsM = arrayOf(
        intArrayOf(16, 10, 1),   // V1 (21x21)
        intArrayOf(28, 16, 1),   // V2 (25x25)
        intArrayOf(44, 26, 1),   // V3 (29x29)
        intArrayOf(64, 18, 2),   // V4 (33x33)
        intArrayOf(86, 24, 2),   // V5 (37x37)
        intArrayOf(108, 16, 4),  // V6 (41x41)
        intArrayOf(124, 18, 4),  // V7 (45x45)
        intArrayOf(154, 22, 4),  // V8 (49x49)
        intArrayOf(182, 22, 5),  // V9 (53x53)
        intArrayOf(216, 26, 5)   // V10 (57x57)
    )

    // Alignment pattern center coordinates for Versions 1..10
    private val alignmentCoords = arrayOf(
        intArrayOf(),            // V1
        intArrayOf(6, 18),       // V2
        intArrayOf(6, 22),       // V3
        intArrayOf(6, 26),       // V4
        intArrayOf(6, 30),       // V5
        intArrayOf(6, 34),       // V6
        intArrayOf(6, 22, 38),   // V7
        intArrayOf(6, 24, 42),   // V8
        intArrayOf(6, 26, 46),   // V9
        intArrayOf(6, 28, 50)    // V10
    )

    // Format info strings for EC Level M (00) and masks 0..7, pre-masked with 0x5412
    private val formatBitsM = intArrayOf(
        0x5412, 0x5125, 0x5E7C, 0x5B4B, 0x45F9, 0x40CE, 0x4F97, 0x4AA0
    )

    // Version info strings (18 bits) for Versions 7..10
    private val versionBits = intArrayOf(
        0x07C94, // V7
        0x085BC, // V8
        0x09A99, // V9
        0x0A4D3  // V10
    )

    fun encode(text: String): QrMatrix {
        val dataBytes = text.encodeToByteArray()
        val version = chooseVersion(dataBytes.size)
        val size = version * 4 + 17
        val spec = versionSpecsM[version - 1]
        val totalDataCodewords = spec[0]
        val ecPerBlock = spec[1]
        val numBlocks = spec[2]

        // 1. Build data bit stream (Byte mode indicator 0100 + length + payload + terminator + pad)
        val bitBuf = ArrayList<Boolean>(totalDataCodewords * 8)
        appendBits(bitBuf, 0b0100, 4) // Byte mode
        val countBits = if (version <= 9) 8 else 16
        appendBits(bitBuf, dataBytes.size, countBits)
        for (b in dataBytes) {
            appendBits(bitBuf, b.toInt() and 0xFF, 8)
        }
        val capacityBits = totalDataCodewords * 8
        val terminatorLen = minOf(4, capacityBits - bitBuf.size)
        appendBits(bitBuf, 0, terminatorLen)
        while (bitBuf.size % 8 != 0) bitBuf.add(false)

        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuf.size < capacityBits) {
            appendBits(bitBuf, padBytes[padIdx % 2], 8)
            padIdx++
        }

        val dataCodewords = IntArray(totalDataCodewords) { i ->
            var v = 0
            for (bit in 0 until 8) {
                v = (v shl 1) or if (bitBuf[i * 8 + bit]) 1 else 0
            }
            v
        }

        // 2. Split into blocks (handling uneven block sizes for V7/V8/V9/V10) & compute RS EC
        val shortBlockLen = totalDataCodewords / numBlocks
        val numLongBlocks = totalDataCodewords % numBlocks
        val numShortBlocks = numBlocks - numLongBlocks

        val dataBlocks = Array(numBlocks) { IntArray(0) }
        val ecBlocks = Array(numBlocks) { IntArray(0) }
        val rsDivisor = rsComputeDivisor(ecPerBlock)

        var offset = 0
        for (b in 0 until numBlocks) {
            val len = shortBlockLen + if (b >= numShortBlocks) 1 else 0
            val block = dataCodewords.copyOfRange(offset, offset + len)
            offset += len
            dataBlocks[b] = block
            ecBlocks[b] = rsComputeRemainder(block, rsDivisor)
        }

        // 3. Interleave data and EC codewords
        val allCodewords = ArrayList<Int>(totalDataCodewords + ecPerBlock * numBlocks)
        val maxDataLen = shortBlockLen + if (numLongBlocks > 0) 1 else 0
        for (i in 0 until maxDataLen) {
            for (b in 0 until numBlocks) {
                if (i < dataBlocks[b].size) allCodewords.add(dataBlocks[b][i])
            }
        }
        for (i in 0 until ecPerBlock) {
            for (b in 0 until numBlocks) {
                allCodewords.add(ecBlocks[b][i])
            }
        }

        // 4. Lay down function patterns
        val isFunction = BooleanArray(size * size)
        val baseModules = BooleanArray(size * size)

        drawFinder(baseModules, isFunction, size, 0, 0)
        drawFinder(baseModules, isFunction, size, 0, size - 7)
        drawFinder(baseModules, isFunction, size, size - 7, 0)

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = (i % 2 == 0)
            setFunction(baseModules, isFunction, size, 6, i, dark)
            setFunction(baseModules, isFunction, size, i, 6, dark)
        }

        // Alignment patterns
        val coords = alignmentCoords[version - 1]
        for (r in coords) {
            for (c in coords) {
                if ((r == 6 && c == 6) ||
                    (r == 6 && c == size - 7) ||
                    (r == size - 7 && c == 6)
                ) continue
                drawAlignment(baseModules, isFunction, size, r, c)
            }
        }

        // Reserve format info areas & dark module
        reserveFormatAreas(baseModules, isFunction, size)
        setFunction(baseModules, isFunction, size, size - 8, 8, true)

        // Version info (V7+)
        if (version >= 7) {
            drawVersionInfo(baseModules, isFunction, size, versionBits[version - 7])
        }

        // 5. Place data bits in upward/downward 2-column zigzag
        var bitIndex = 0
        val totalBits = allCodewords.size * 8
        var col = size - 1
        var upward = true
        while (col >= 1) {
            if (col == 6) col-- // Skip vertical timing column
            for (step in 0 until size) {
                val r = if (upward) size - 1 - step else step
                for (dx in 0..1) {
                    val c = col - dx
                    if (!isFunction[r * size + c]) {
                        val bit = if (bitIndex < totalBits) {
                            ((allCodewords[bitIndex ushr 3] ushr (7 - (bitIndex and 7))) and 1) != 0
                        } else {
                            false
                        }
                        baseModules[r * size + c] = bit
                        bitIndex++
                    }
                }
            }
            upward = !upward
            col -= 2
        }

        // 6. Try all 8 masks, pick lowest penalty score
        var bestMask = 0
        var bestScore = Int.MAX_VALUE
        var bestModules = baseModules
        for (mask in 0..7) {
            val candidate = baseModules.copyOf()
            applyMask(candidate, isFunction, size, mask)
            drawFormatInfo(candidate, size, formatBitsM[mask])
            val score = computePenaltyScore(candidate, size)
            if (score < bestScore) {
                bestScore = score
                bestMask = mask
                bestModules = candidate
            }
        }
        // Ensure format info matches bestMask (already applied in bestModules)
        drawFormatInfo(bestModules, size, formatBitsM[bestMask])
        return QrMatrix(size = size, modules = bestModules)
    }

    private fun chooseVersion(byteLen: Int): Int {
        for (v in 1..versionSpecsM.size) {
            val headerBits = 4 + if (v <= 9) 8 else 16
            val capacityBytes = (versionSpecsM[v - 1][0] * 8 - headerBits) / 8
            if (byteLen <= capacityBytes) return v
        }
        return versionSpecsM.size
    }

    private fun appendBits(buf: MutableList<Boolean>, value: Int, bitCount: Int) {
        for (i in bitCount - 1 downTo 0) {
            buf.add(((value ushr i) and 1) != 0)
        }
    }

    private fun setFunction(modules: BooleanArray, isFunc: BooleanArray, size: Int, r: Int, c: Int, dark: Boolean) {
        if (r in 0 until size && c in 0 until size) {
            modules[r * size + c] = dark
            isFunc[r * size + c] = true
        }
    }

    private fun drawFinder(modules: BooleanArray, isFunc: BooleanArray, size: Int, top: Int, left: Int) {
        for (dr in -1..7) {
            for (dc in -1..7) {
                val r = top + dr
                val c = left + dc
                if (r !in 0 until size || c !in 0 until size) continue
                val dark = dr in 0..6 && dc in 0..6 &&
                    (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4))
                setFunction(modules, isFunc, size, r, c, dark)
            }
        }
    }

    private fun drawAlignment(modules: BooleanArray, isFunc: BooleanArray, size: Int, centerR: Int, centerC: Int) {
        for (dr in -2..2) {
            for (dc in -2..2) {
                val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                setFunction(modules, isFunc, size, centerR + dr, centerC + dc, dist != 1)
            }
        }
    }

    private fun reserveFormatAreas(modules: BooleanArray, isFunc: BooleanArray, size: Int) {
        for (i in 0..8) {
            if (i != 6) {
                setFunction(modules, isFunc, size, 8, i, false)
                setFunction(modules, isFunc, size, i, 8, false)
            }
        }
        for (i in 0..7) {
            setFunction(modules, isFunc, size, 8, size - 1 - i, false)
        }
        for (i in 0..6) {
            setFunction(modules, isFunc, size, size - 1 - i, 8, false)
        }
    }

    private fun drawFormatInfo(modules: BooleanArray, size: Int, bits: Int) {
        // First copy: around top-left finder
        for (i in 0..5) modules[8 * size + i] = ((bits ushr (14 - i)) and 1) != 0
        modules[8 * size + 7] = ((bits ushr 8) and 1) != 0
        modules[8 * size + 8] = ((bits ushr 7) and 1) != 0
        modules[7 * size + 8] = ((bits ushr 6) and 1) != 0
        for (i in 0..5) modules[(5 - i) * size + 8] = ((bits ushr (5 - i)) and 1) != 0

        // Second copy: bottom-left vertical & top-right horizontal
        for (i in 0..6) modules[(size - 1 - i) * size + 8] = ((bits ushr (14 - i)) and 1) != 0
        for (i in 0..7) modules[8 * size + (size - 8 + i)] = ((bits ushr (7 - i)) and 1) != 0
    }

    private fun drawVersionInfo(modules: BooleanArray, isFunc: BooleanArray, size: Int, bits: Int) {
        for (i in 0 until 18) {
            val dark = ((bits ushr i) and 1) != 0
            val a = size - 11 + (i % 3)
            val b = i / 3
            setFunction(modules, isFunc, size, a, b, dark)
            setFunction(modules, isFunc, size, b, a, dark)
        }
    }

    private fun applyMask(modules: BooleanArray, isFunc: BooleanArray, size: Int, mask: Int) {
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (isFunc[r * size + c]) continue
                val invert = when (mask) {
                    0 -> (r + c) % 2 == 0
                    1 -> r % 2 == 0
                    2 -> c % 3 == 0
                    3 -> (r + c) % 3 == 0
                    4 -> ((r / 2) + (c / 3)) % 2 == 0
                    5 -> ((r * c) % 2) + ((r * c) % 3) == 0
                    6 -> ((((r * c) % 2) + ((r * c) % 3)) % 2) == 0
                    7 -> ((((r + c) % 2) + ((r * c) % 3)) % 2) == 0
                    else -> false
                }
                if (invert) modules[r * size + c] = !modules[r * size + c]
            }
        }
    }

    private fun computePenaltyScore(modules: BooleanArray, size: Int): Int {
        var penalty = 0
        // Rule 1: runs of 5+ same-color modules in rows & cols
        for (r in 0 until size) {
            var runLen = 1
            var prev = modules[r * size]
            for (c in 1 until size) {
                val cur = modules[r * size + c]
                if (cur == prev) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    prev = cur
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        for (c in 0 until size) {
            var runLen = 1
            var prev = modules[c]
            for (r in 1 until size) {
                val cur = modules[r * size + c]
                if (cur == prev) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    prev = cur
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        // Rule 2: 2x2 blocks of same color
        for (r in 0 until size - 1) {
            for (c in 0 until size - 1) {
                val color = modules[r * size + c]
                if (color == modules[r * size + c + 1] &&
                    color == modules[(r + 1) * size + c] &&
                    color == modules[(r + 1) * size + c + 1]
                ) {
                    penalty += 3
                }
            }
        }
        // Rule 4: dark/light balance
        var darkCount = 0
        for (m in modules) if (m) darkCount++
        val total = size * size
        val k = kotlin.math.abs(darkCount * 20 - total * 10) / total
        penalty += k * 10
        return penalty
    }

    // GF(256) with primitive polynomial 0x11D
    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        var a = x
        var b = y
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((b ushr i) and 1) * a)
        }
        return z
    }

    private fun rsComputeDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) {
                    result[j] = result[j] xor result[j + 1]
                }
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun rsComputeRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            for (i in 0 until result.size - 1) {
                result[i] = result[i + 1]
            }
            result[result.size - 1] = 0
            for (i in divisor.indices) {
                result[i] = result[i] xor gfMultiply(divisor[i], factor)
            }
        }
        return result
    }
}
