pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        // مرآة احتياطية: مكوّنات نادرة (مثل de.rototor.pdfbox:graphics2d لتصدير PDF)
        // لا تعكسها علي‌يون دائمًا
        maven("https://repo.huaweicloud.com/repository/maven/")
        mavenCentral()
        google()
    }
}

rootProject.name = "school-radio-desktop"
