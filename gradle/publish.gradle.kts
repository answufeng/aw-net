apply(plugin = "maven-publish")

extensions.configure<org.gradle.api.publish.PublishingExtension> {
    publications {
        register<org.gradle.api.publish.maven.MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }

            groupId = "com.github.answufeng"
            artifactId = "aw-net"
            version = property("VERSION_NAME")?.toString() ?: "1.0.0"

            pom {
                name.set("aw-net")
                description.set("Android network library based on OkHttp + Retrofit + Hilt")
                url.set("https://github.com/answufeng/aw-net")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("answufeng")
                        name.set("answufeng")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/answufeng/aw-net.git")
                    url.set("https://github.com/answufeng/aw-net")
                }
            }
        }
    }
}
