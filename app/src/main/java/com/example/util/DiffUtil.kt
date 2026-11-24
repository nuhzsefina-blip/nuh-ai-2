package com.example.util

enum class DiffType {
    UNCHANGED,
    ADDED,
    REMOVED,
}

data class DiffLine(
    val type: DiffType,
    val text: String,
)

object TextDiffUtil {
    private const val MAX_DP_CELLS = 50_000

    fun computeLineDiff(original: String, revised: String): List<DiffLine> {
        if (original == revised) {
            return original.split("\n").map { DiffLine(DiffType.UNCHANGED, it) }
        }
        val origLines = original.split("\n")
        val revLines = revised.split("\n")
        val n = origLines.size
        val m = revLines.size

        var prefix = 0
        val minLen = minOf(n, m)
        while (prefix < minLen && origLines[prefix] == revLines[prefix]) {
            prefix++
        }

        var suffix = 0
        while (suffix < (n - prefix) && suffix < (m - prefix) &&
            origLines[n - 1 - suffix] == revLines[m - 1 - suffix]
        ) {
            suffix++
        }

        val result = ArrayList<DiffLine>(maxOf(n, m) + 8)
        for (idx in 0 until prefix) {
            result.add(DiffLine(DiffType.UNCHANGED, origLines[idx]))
        }

        val remN = n - prefix - suffix
        val remM = m - prefix - suffix

        if (remN.toLong() * remM.toLong() > MAX_DP_CELLS) {
            for (idx in prefix until (n - suffix)) {
                result.add(DiffLine(DiffType.REMOVED, origLines[idx]))
            }
            for (idx in prefix until (m - suffix)) {
                result.add(DiffLine(DiffType.ADDED, revLines[idx]))
            }
        } else if (remN > 0 || remM > 0) {
            val dp = Array(remN + 1) { IntArray(remM + 1) }
            for (i in remN - 1 downTo 0) {
                val origLine = origLines[prefix + i]
                val row = dp[i]
                val nextRow = dp[i + 1]
                for (j in remM - 1 downTo 0) {
                    row[j] = if (origLine == revLines[prefix + j]) {
                        nextRow[j + 1] + 1
                    } else {
                        maxOf(nextRow[j], row[j + 1])
                    }
                }
            }

            var i = 0
            var j = 0
            while (i < remN && j < remM) {
                val oLine = origLines[prefix + i]
                val rLine = revLines[prefix + j]
                when {
                    oLine == rLine -> {
                        result.add(DiffLine(DiffType.UNCHANGED, oLine))
                        i++
                        j++
                    }
                    dp[i + 1][j] >= dp[i][j + 1] -> {
                        result.add(DiffLine(DiffType.REMOVED, oLine))
                        i++
                    }
                    else -> {
                        result.add(DiffLine(DiffType.ADDED, rLine))
                        j++
                    }
                }
            }
            while (i < remN) {
                result.add(DiffLine(DiffType.REMOVED, origLines[prefix + i++]))
            }
            while (j < remM) {
                result.add(DiffLine(DiffType.ADDED, revLines[prefix + j++]))
            }
        }

        for (idx in (n - suffix) until n) {
            result.add(DiffLine(DiffType.UNCHANGED, origLines[idx]))
        }
        return result
    }
}
