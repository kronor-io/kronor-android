plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "kronor.android.application"
            implementationClass = "KronorAndroidApplicationPlugin"
        }
        register("androidLibrary") {
            id = "kronor.android.library"
            implementationClass = "KronorAndroidLibraryPlugin"
        }
        register("androidCompose") {
            id = "kronor.android.compose"
            implementationClass = "KronorAndroidComposePlugin"
        }
        register("androidPublishedLibrary") {
            id = "kronor.android.published-library"
            implementationClass = "KronorAndroidPublishedLibraryPlugin"
        }
    }
}
