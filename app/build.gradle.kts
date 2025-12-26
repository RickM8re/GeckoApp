import com.android.build.api.artifact.SingleArtifact
import groovy.json.JsonOutput
import java.time.LocalDateTime


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "re.rickmoo.gecko"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "re.rickmoo.gecko"
        minSdk = 28
        targetSdk = 36
        versionCode = rootProject.ext["versionCode"].toString().toInt()
        val tagName = rootProject.ext["tagName"].toString()
        versionName = "${if (tagName.isBlank()) "" else "$tagName."}r$versionCode.${rootProject.ext["hash"].toString()}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}


androidComponents {

    val tagName = rootProject.ext["tagName"].toString()
    val outputsDir = rootProject.ext["outputsDir"].toString()
    val versionCode = rootProject.ext["versionCode"].toString().toInt()
    val versionName =
        "${if (tagName.isBlank()) "" else "$tagName."}r$versionCode.${rootProject.ext["hash"].toString()}"

    fun nameToAbi(name: String): String {
        return when {
            name.contains("arm64-v8a") -> "arm64-v8a"
            name.contains("armeabi-v7a") -> "armeabi-v7a"
            name.contains("x86_64") -> "x86_64"
            name.contains("x86") -> "x86"
            else -> "noarch"
        }
    }

    onVariants { variant ->
        val copyTaskName = "copy${variant.name.replaceFirstChar { it.uppercase() }}Apk"

        val copyTask = tasks.register<Copy>(copyTaskName) {
            description = "Copies and renames the APK for variant ${variant.name}"
            group = "distribution"
            from(variant.artifacts.get(SingleArtifact.APK))

            into(layout.projectDirectory.dir("${outputsDir}/${variant.name}"))

            rename { originalFileName ->
                // 格式: ProjectName-Version-ABI-BuildType.apk
                "${rootProject.name}-${versionName}-${nameToAbi(originalFileName)}-${variant.name}.apk"
            }
        }
        val assembleTaskName = "assemble${variant.name.replaceFirstChar { it.uppercase() }}"
        tasks.matching { it.name == assembleTaskName }.configureEach {
            finalizedBy(copyTask)
        }
        tasks.register("generateIndex${variant.name.replaceFirstChar { it.uppercase() }}") {
            group = "documentation"
            description = "Generates apk index info"
            // 【关键点 A】获取 Copy 任务的目标路径
            // map { } 会在配置阶段懒加载，直到任务执行时才获取真实值
            dependsOn(copyTask)
            val outputDirProvider = copyTask.map { it.destinationDir }
            inputs.dir(outputDirProvider)

            doLast {
                val dir = outputDirProvider.get()

                // 1. 获取最终的 APK 文件名
                // 因为 Copy 任务重命名了文件，我们需要在目录下找到生成的那个 .apk 文件
                val apkFile = dir.listFiles { _, name -> name.endsWith(".apk") }

                if (apkFile != null) {
                    // 2. 准备数据
                    val tagName = rootProject.ext["tagName"].toString()
                    val dateStr = LocalDateTime.now().toString()

                    val newEntry = mapOf(
                        "version" to tagName.ifBlank { versionName },
                        "date" to dateStr,
                        "type" to if (tagName.isBlank()) "nightly" else "release",
                        "apks" to apkFile.associate {
                            nameToAbi(it.name) to it.name
                        }
                    )

                    val indexFile = File(dir, "index.json")
                    val jsonString = JsonOutput.prettyPrint(JsonOutput.toJson(listOf(newEntry)))
                    indexFile.writeText(jsonString)
                    File(dir.parentFile.parentFile, "latest-${variant.name}.json").writeText(jsonString)
                    println("Index generated at: ${indexFile.absolutePath}")
                    println("Entry: $newEntry")
                } else {
                    println("Skipping index generation: No APK found in ${dir.absolutePath}")
                }
            }
        }.also {
            tasks.matching { it.name == assembleTaskName }.configureEach { finalizedBy(it) }
        }
    }

}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)


    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlin.reflect)
    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    // https://mvnrepository.com/artifact/com.squareup.retrofit2/retrofit
    implementation(libs.retrofit)
    implementation(libs.converter.jackson)
    // https://mvnrepository.com/artifact/org.mozilla.geckoview/geckoview
    implementation(libs.geckoview)
    implementation(libs.glide)
    implementation(libs.compose.markdown)
}
kotlin {
    jvmToolchain(21)
}