/**
 * Gradle 多项目构建配置
 *
 * 自动扫描根目录下包含 .gradle.kts 构建文件的子目录，注册为子项目。
 */

val useAliyunMirror = providers.gradleProperty("useAliyunMirror")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = rootDir.name

// ── 自动发现并注册所有子项目 ──

val excludedDirs = setOf(".git", ".gradle", ".idea", "buildSrc", "build", "gradle", "src", "logs", "profile")
val buildFileExtension = ".gradle.kts"

fileTree(rootDir).matching {
    include("**/*$buildFileExtension")
    exclude(excludedDirs.map { "**/$it/**" })
}.files.mapNotNull { file ->
    file.parentFile?.takeIf { it.isPotentialProject(excludedDirs) }
}.toSet().forEach { it.registerAsSubproject() }

// ── 工具函数 ──

fun File.isPotentialProject(excluded: Set<String>): Boolean {
    if (!isDirectory || this == rootDir) return false
    // 检查当前目录及所有祖先目录是否在排除列表中
    var current: File? = this
    while (current != null && current != rootDir) {
        if (current.name in excluded) return false
        current = current.parentFile
    }
    return listFiles()?.any { it.name.endsWith(buildFileExtension) } == true
}

fun File.registerAsSubproject() {
    val projectName = toRelativeString(rootDir).replace(File.separator, ":")
    val buildFile = listFiles()?.find { it.name.endsWith(buildFileExtension) }

    include(":$projectName")
    project(":$projectName").apply {
        projectDir = this@registerAsSubproject
        buildFileName = buildFile?.name ?: "${name}$buildFileExtension"
    }
}

