package com.ephemeral.chat.plugins.location

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geohash 编码工具。
 * 将经纬度编码为指定精度的 Geohash 字符串。
 * 精度 5 ≈ 5km × 5km 网格。
 */
object GeohashUtils {

    private val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray()

    /**
     * 编码：经纬度 → Geohash 字符串。
     * 标准二分查找算法，交替处理经度和纬度。
     *
     * @param lat 纬度 [-90, 90]
     * @param lon 经度 [-180, 180]
     * @param precision 精度，默认 5
     * @return Geohash 字符串
     */
    fun encode(lat: Double, lon: Double, precision: Int = 5): String {
        val geohash = StringBuilder()
        var isEven = true
        var currentLat = lat.coerceIn(-90.0, 90.0)
        var currentLon = lon.coerceIn(-180.0, 180.0)
        var latRange = doubleArrayOf(-90.0, 90.0)
        var lonRange = doubleArrayOf(-180.0, 180.0)

        while (geohash.length < precision) {
            val mid: Double
            if (isEven) {
                mid = (lonRange[0] + lonRange[1]) / 2
                if (currentLon >= mid) {
                    geohash.append('1')
                    lonRange[0] = mid
                } else {
                    geohash.append('0')
                    lonRange[1] = mid
                }
            } else {
                mid = (latRange[0] + latRange[1]) / 2
                if (currentLat >= mid) {
                    geohash.append('1')
                    latRange[0] = mid
                } else {
                    geohash.append('0')
                    latRange[1] = mid
                }
            }
            isEven = !isEven
        }

        // 将二进制字符串转为 base32
        val binaryStr = geohash.toString()
        val result = StringBuilder()
        var i = 0
        while (i < binaryStr.length) {
            val end = minOf(i + 5, binaryStr.length)
            val chunk = binaryStr.substring(i, end)
            // 不足 5 位时右侧补 0
            val padded = chunk.padEnd(5, '0')
            val index = padded.toInt(2)
            result.append(BASE32[index])
            i += 5
        }
        return result.toString()
    }

    /**
     * 计算给定 Geohash 的 8 个邻域（含自身共 9 个）。
     * 使用边界框方法计算相邻格子。
     *
     * @param geohash Geohash 字符串
     * @return 9 个邻域的 Geohash 列表（含自身）
     */
    fun neighbors(geohash: String): List<String> {
        val result = mutableListOf<String>()
        // 解码中心点的边界框
        val (centerLat, centerLon) = decodeCenter(geohash)
        val (latErr, lonErr) = decodeError(geohash)

        // 9 个方向偏移（自身 + 8 邻域）
        val offsets = listOf(
            0 to 0,           // 自身
            0 to 1,           // E
            0 to -1,          // W
            1 to 0,           // N
            -1 to 0,          // S
            1 to 1,           // NE
            1 to -1,          // NW
            -1 to 1,          // SE
            -1 to -1,         // SW
        )

        for ((dLat, dLon) in offsets) {
            // 每个方向偏移一个格子大小
            val neighborLat = centerLat + dLat * latErr * 2
            val neighborLon = centerLon + dLon * lonErr * 2
            result.add(encode(neighborLat, neighborLon, geohash.length))
        }
        return result
    }

    /**
     * 计算两点间距离（米），Haversine 公式。
     * 地球半径 6,371,000 米。
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2).pow(2.0) +
            cos(lat1 * PI / 180) * cos(lat2 * PI / 180) *
            sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * 解码 Geohash 的中心点经纬度。
     */
    private fun decodeCenter(geohash: String): Pair<Double, Double> {
        val (minLat, maxLat, minLon, maxLon) = decodeBox(geohash)
        return Pair((minLat + maxLat) / 2, (minLon + maxLon) / 2)
    }

    /**
     * 解码 Geohash 的经纬度误差（半边长）。
     */
    private fun decodeError(geohash: String): Pair<Double, Double> {
        val (minLat, maxLat, minLon, maxLon) = decodeBox(geohash)
        return Pair((maxLat - minLat) / 2, (maxLon - minLon) / 2)
    }

    /**
     * 解码 Geohash 对应的边界框。
     */
    private fun decodeBox(geohash: String): DoubleArray {
        var isEven = true
        var latRange = doubleArrayOf(-90.0, 90.0)
        var lonRange = doubleArrayOf(-180.0, 180.0)

        for (char in geohash) {
            val index = BASE32.indexOf(char)
            if (index < 0) continue
            var bits = index
            for (bit in 4 downTo 0) {
                val valBit = (bits shr bit) and 1
                if (isEven) {
                    val mid = (lonRange[0] + lonRange[1]) / 2
                    if (valBit == 1) lonRange[0] = mid else lonRange[1] = mid
                } else {
                    val mid = (latRange[0] + latRange[1]) / 2
                    if (valBit == 1) latRange[0] = mid else latRange[1] = mid
                }
                isEven = !isEven
            }
        }
        return doubleArrayOf(latRange[0], latRange[1], lonRange[0], lonRange[1])
    }
}
