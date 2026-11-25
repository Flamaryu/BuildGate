import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

abstract class GenerateBuildGateEnvTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    abstract val localPropertiesFile: RegularFileProperty

    @get:Input
    abstract val envSupabaseUrl: Property<String>

    @get:Input
    abstract val envSupabaseKey: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val props = Properties()
        val file = localPropertiesFile.orNull?.asFile
        if (file != null && file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        val rawUrl = (props.getProperty("SUPABASE_URL") ?: envSupabaseUrl.get()).trim()
        val rawKey = (props.getProperty("SUPABASE_PUBLISHABLE_KEY")
            ?: props.getProperty("SUPABASE_ANON_KEY")
            ?: envSupabaseKey.get()).trim()

        val escapedUrl = rawUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedKey = rawKey.replace("\\", "\\\\").replace("\"", "\\\"")

        val outFile = outputDir.get().file("dev/buildgate/app/shared/network/GeneratedSupabaseEnv.kt").asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            package dev.buildgate.app.shared.network

            internal object GeneratedSupabaseEnv {
                const val SUPABASE_URL: String = "$escapedUrl"
                const val SUPABASE_PUBLISHABLE_KEY: String = "$escapedKey"
            }
            """.trimIndent() + "\n"
        )
    }
}

val generateBuildGateEnvConfig = tasks.register<GenerateBuildGateEnvTask>("generateBuildGateEnvConfig") {
    val lp = rootProject.layout.projectDirectory.file("local.properties")
    if (lp.asFile.exists()) {
        localPropertiesFile.set(lp)
    }
    envSupabaseUrl.set(providers.environmentVariable("SUPABASE_URL").orElse(""))
    envSupabaseKey.set(
        providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY")
            .orElse(providers.environmentVariable("SUPABASE_ANON_KEY"))
            .orElse("")
    )
    outputDir.set(layout.buildDirectory.dir("generated/buildgateEnv/commonMain/kotlin"))
}

kotlin {
    js {
        browser()
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    android {
       namespace = "dev.buildgate.app.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.cio)
        }
        commonMain {
            kotlin.srcDir(generateBuildGateEnvConfig)
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.material3.windowSizeClass)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.androidx.navigation.compose)

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.supabase.auth)
                implementation(libs.supabase.postgrest)
                implementation(libs.supabase.storage)
                implementation(libs.supabase.realtime)
                implementation(libs.supabase.functions)

                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.ktor.client.js)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
