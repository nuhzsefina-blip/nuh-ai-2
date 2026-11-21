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
    fun computeLineDiff(original: String, revised: String): List<DiffLine> {
        val origLines = original.split("\n")
        val revLines = revised.split("\n")
        val n = origLines.size
        val m = revLines.size

        // LCS DP table
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (origLines[i] == revLines[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val result = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                origLines[i] == revLines[j] -> {
                    result.add(DiffLine(DiffType.UNCHANGED, origLines[i]))
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    result.add(DiffLine(DiffType.REMOVED, origLines[i]))
                    i++
                }
                else -> {
                    result.add(DiffLine(DiffType.ADDED, revLines[j]))
                    j++
                }
            }
        }
        while (i < n) {
            result.add(DiffLine(DiffType.REMOVED, origLines[i++]))
        }
        while (j < m) {
            result.add(DiffLine(DiffType.ADDED, revLines[j++]))
        }
        return result
    }
}
