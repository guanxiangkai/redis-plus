/**
 * 版本号分类工具
 *
 * 规范：
 * - 正式版    `2026.0.0`                                → AUTOMATIC（自动发布到 Maven Central）
 * - 预发布    `2026.0.0-M1 / -RC1 / -alpha1 / -beta2`   → AUTOMATIC（自动发布到 Maven Central）
 * - 快照版    `2026.0.0-SNAPSHOT`                        → 禁止上传 Central Portal
 */
data class VersionClassification(val version: String) {

    val isSnapshot: Boolean =
        version.endsWith("-SNAPSHOT", ignoreCase = true)

    val isPreRelease: Boolean =
        !isSnapshot && PRE_RELEASE_PATTERN.matches(version)

    val isRelease: Boolean =
        !isSnapshot && !isPreRelease

    /** Central Portal `publishingType` 参数 */
    val publishingType: String =
        if (!isSnapshot) "AUTOMATIC" else "NONE"

    /** 用于日志的版本标签 */
    val label: String = when {
        isRelease -> "正式版 ✅"
        isPreRelease -> "预发布 🟡"
        else -> "快照版 🔴"
    }

    companion object {
        private val PRE_RELEASE_PATTERN = Regex(
            ".*-(?:M\\d+|RC\\d+|alpha\\.?\\d*|beta\\.?\\d*|CR\\d+)",
            RegexOption.IGNORE_CASE
        )
    }
}

