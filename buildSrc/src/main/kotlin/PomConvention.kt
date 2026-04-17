import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom

/**
 * POM 元数据配置（Maven Central 要求）
 *
 * 所有可配置项均从 `gradle.properties` 读取，前缀为 `pom.`：
 * ```properties
 * pom.description=...
 * pom.url=...
 * pom.license.name=...
 * pom.license.url=...
 * pom.developer.id=...
 * pom.developer.name=...
 * pom.scm.url=...
 * pom.scm.connection=...
 * pom.scm.developerConnection=...
 * ```
 */
fun MavenPom.configureRedisPlusPom(project: Project, moduleName: String) {

    fun prop(key: String): String =
        project.rootProject.findProperty(key)?.toString()
            ?: error("gradle.properties 缺少必需属性: $key")

    name.set(moduleName)
    description.set("${prop("pom.description")} :: $moduleName")
    url.set(prop("pom.url"))

    licenses {
        license {
            name.set(prop("pom.license.name"))
            url.set(prop("pom.license.url"))
        }
    }
    developers {
        developer {
            id.set(prop("pom.developer.id"))
            name.set(prop("pom.developer.name"))
        }
    }
    scm {
        url.set(prop("pom.scm.url"))
        connection.set(prop("pom.scm.connection"))
        developerConnection.set(prop("pom.scm.developerConnection"))
    }
}

