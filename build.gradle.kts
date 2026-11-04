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
    // فكّ صيغ الموسيقى الحديثة: OGG Vorbis وFLAC — قرّاء نقية على JVM
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("org.jflac:jflac-codec:1.5.2")
    // استيراد اليوتيوب: فكّ AAC/M4A (JAAD — Public Domain) ثم ترميز MP3 نقي (jump3r/LAME — LGPL)
    implementation("de.sfuhrm:jaad:0.8.7")
    implementation("de.sciss:jump3r:1.0.5")
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

// أداة تحميل اليوتيوب مدمجة لكل منصة في نسختها فقط (v0.25.0):
// CI يبني كل جرّة على منصتها (windows/ubuntu/macos) فتحمل الجرّة أداة منصتها وحدها،
// والـ MSI (يُبنى على ويندوز) يأخذ أداة ويندوز — بلا تفريخ في حجم الجرات الأخرى.
val ytdlpOs: String = run {
    val n = System.getProperty("os.name").lowercase()
    when {
        n.contains("win") -> "windows"
        n.contains("mac") || n.contains("darwin") -> "macos"
        else -> "linux"
    }
}
val ytdlpBinary = rootProject.file(
    "binaries/yt-dlp/$ytdlpOs/" + if (ytdlpOs == "windows") "yt-dlp.exe" else "yt-dlp"
)

tasks.processResources {
    if (ytdlpBinary.isFile) {
        from(ytdlpBinary) { into("bin") }
        from(rootProject.file("binaries/yt-dlp/versions.txt")) { into("bin") }
        rename { n -> if (n == "versions.txt") "ytdlp_version.txt" else n }
    }
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
                // أيقونة المثبّت والمختصر — نفس شعار الواجهة (v0.22.0)
                iconFile = rootProject.file("packaging/icon.ico")
            }
        }
    }
}
