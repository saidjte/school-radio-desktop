import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

// version.properties هو المصدر الوحيد للحقيقة بشأن الإصدار (كما في نسخة الهاتف)
val versionProps = Properties().apply {
    rootProject.file("version.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val appVersion = versionProps.getProperty("versionName") ?: "0.0.0"

group = "com.saidjte.schoolradio"
version = appVersion

// المستودعات معلنة في settings.gradle.kts (dependencyResolutionManagement)

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("javazoom:jlayer:1.0.1")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// توليد ملف ثابت للإصدار من version.properties (مصدر وحيد للحقيقة)
val generatedVersionDir = layout.buildDirectory.dir("generated/appversion/kotlin")

sourceSets {
    main {
        kotlin.srcDir(generatedVersionDir)
    }
}

tasks.register("generateAppVersion") {
    // version.properties مدخل مُعلَن: أي تغيير في الإصدار يجبر إعادة التوليد
    // (بدونها تبقى الجرة تحمل رقم إصدار قديمًا رغم اسمها الجديد)
    inputs.file(rootProject.file("version.properties"))
    outputs.dir(generatedVersionDir)
    doLast {
        val dir = generatedVersionDir.get().asFile
        dir.mkdirs()
        dir.resolve("AppVersion.kt").writeText(
            """package com.saidjte.schoolradio.desktop

const val APP_VERSION = "$appVersion"
""",
            Charsets.UTF_8
        )
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateAppVersion")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

compose.desktop {
    application {
        mainClass = "com.saidjte.schoolradio.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "SchoolRadioDesktop"
            packageVersion = appVersion
            vendor = "saidjte"
            includeAllModules = true

            windows {
                dirChooser = true
                menu = true
                shortcut = true
            }
        }
    }
}
