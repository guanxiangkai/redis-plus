import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip

/**
 * Central Portal 发布流水线（三步：清理 → 打包 → 上传）
 *
 * 在根项目中调用即可注册全部三个 Task：
 * ```kotlin
 * registerCentralPortalTasks(ver, sonatypeUser, sonatypePass, stagingDir)
 * ```
 */
fun Project.registerCentralPortalTasks(
    ver: VersionClassification,
    sonatypeUser: String,
    sonatypePass: String,
    stagingDir: Provider<Directory>,
) {
    // ① 清理上次暂存产物
    val cleanStaging = tasks.register("cleanStaging", Delete::class.java) {
        group = "publishing"
        description = "清理 Central Portal 本地暂存目录"
        delete(stagingDir)
    }

    // ② 将所有子模块产物打成一个 ZIP 部署包
    val bundleForCentralPortal = tasks.register("bundleForCentralPortal", Zip::class.java) {
        group = "publishing"
        description = "将暂存仓库打包为 Central Portal 部署包（ZIP）"
        dependsOn(cleanStaging)
        dependsOn(subprojects.map { it.tasks.named("publishMavenJavaPublicationToStagingRepository") })
        from(stagingDir)
        archiveFileName.set("central-bundle.zip")
        destinationDirectory.set(layout.buildDirectory.dir("central-portal"))
    }

    // ③ 上传 ZIP 到 Sonatype Central Portal
    tasks.register("publishToCentralPortal", Exec::class.java) {
        group = "publishing"
        description = "上传部署包到 Central Portal（正式版 & 预发布均 AUTOMATIC / 快照禁止）"
        dependsOn(bundleForCentralPortal)

        val bundleFile = layout.buildDirectory.file("central-portal/central-bundle.zip")

        doFirst {
            require(!ver.isSnapshot) { "❌ SNAPSHOT 版本 (${ver.version}) 不允许上传 Central Portal" }
            require(sonatypeUser.isNotBlank()) { "请在 ~/.gradle/gradle.properties 中配置 sonatypeUsername" }
            require(sonatypePass.isNotBlank()) { "请在 ~/.gradle/gradle.properties 中配置 sonatypePassword" }
            require(bundleFile.get().asFile.exists()) { "部署包不存在: ${bundleFile.get().asFile.path}" }

            val emoji = if (ver.isRelease) "🚀" else "🟡"
            println("$emoji 正在上传 ${ver.version} 到 Central Portal（publishingType=${ver.publishingType}）…")
        }

        commandLine(
            "curl", "--fail-with-body",
            "-X", "POST",
            "https://central.sonatype.com/api/v1/publisher/upload",
            "-u", "$sonatypeUser:$sonatypePass",
            "-F", "bundle=@${bundleFile.get().asFile.path}",
            "-F", "publishingType=${ver.publishingType}"
        )
    }
}

