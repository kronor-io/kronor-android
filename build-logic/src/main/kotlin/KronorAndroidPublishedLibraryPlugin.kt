import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningExtension

class KronorAndroidPublishedLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("kronor.android.library")
        pluginManager.apply("maven-publish")
        pluginManager.apply("signing")

        val kronorPublishing = extensions.create<KronorPublishingExtension>("kronorPublishing")

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications.create<MavenPublication>("release") {
                    groupId = kronorPublishing.groupId.get()
                    artifactId = kronorPublishing.artifactId.get()
                    version = rootProject.extensions.extraProperties["version"].toString()
                    from(components.getByName("release"))

                    pom {
                        name.set(kronorPublishing.displayName)
                        description.set(kronorPublishing.description)
                        url.set("https://github.com/kronor-io/kronor-android")
                        licenses {
                            license {
                                name.set("MIT License")
                                url.set("https://github.com/kronor-io/kronor-android/blob/main/LICENSE")
                            }
                        }
                        developers {
                            developer {
                                id.set("iAmMrinal0")
                                name.set("Mrinal Purohit")
                                email.set("mrinal@kronor.io")
                            }
                            developer {
                                id.set("pranaysashank")
                                name.set("Pranay Sashank")
                                email.set("pranay.sashank@kronor.io")
                            }
                        }
                        scm {
                            connection.set("scm:git:github.com/kronor-io/kronor-android.git")
                            developerConnection.set("scm:git:ssh://github.com/kronor-io/kronor-android.git")
                            url.set("https://github.com/kronor-io/kronor-android/tree/main")
                        }
                    }
                }
            }

            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(
                    rootProject.extensions.extraProperties["signing.keyId"].toString(),
                    rootProject.extensions.extraProperties["signing.key"].toString(),
                    rootProject.extensions.extraProperties["signing.password"].toString(),
                )
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }
}
