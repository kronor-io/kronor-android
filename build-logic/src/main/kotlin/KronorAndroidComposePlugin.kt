import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class KronorAndroidComposePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val composeBom = libs.findLibrary("androidx-compose-bom").get()
        dependencies.add("implementation", dependencies.platform(composeBom))
        dependencies.add("androidTestImplementation", dependencies.platform(composeBom))

        pluginManager.withPlugin("com.android.application") {
            extensions.configure(ApplicationExtension::class.java) {
                buildFeatures {
                    compose = true
                    viewBinding = true
                }
                defaultConfig.vectorDrawables.useSupportLibrary = true
                packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

        pluginManager.withPlugin("com.android.library") {
            extensions.configure(LibraryExtension::class.java) {
                buildFeatures {
                    compose = true
                    viewBinding = true
                }
                defaultConfig.vectorDrawables.useSupportLibrary = true
                packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

        extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
            if (providers.gradleProperty("composeCompilerReports").orNull == "true") {
                reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
            }
            if (providers.gradleProperty("composeCompilerMetrics").orNull == "true") {
                metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
            }
        }
    }
}
