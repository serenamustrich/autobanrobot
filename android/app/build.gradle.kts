import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autobanrobot.mobile"
    compileSdk = 35

    val releaseKeystore = rootProject.file("../.private/android/AutoBanRobot-release.jks")
    val releaseSigningProperties = Properties().apply {
        val localSigningFile = rootProject.file("../签名资料")
        if (localSigningFile.isFile) {
            localSigningFile.inputStream().use(::load)
        }
    }
    fun releaseSigningValue(name: String): String? =
        providers.environmentVariable(name).orNull ?: releaseSigningProperties.getProperty(name)
    val releaseStorePassword = releaseSigningValue("AUTOBAN_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = releaseSigningValue("AUTOBAN_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = releaseSigningValue("AUTOBAN_RELEASE_KEY_PASSWORD")

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    defaultConfig {
        applicationId = "com.autobanrobot.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 61
        versionName = "1.0.60"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/shared-assets"))
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/shared-res"))
}

val syncSharedAssets by tasks.registering(Copy::class) {
    from(rootProject.file("../injected.js")) { rename { "content/injected.js" } }
    from(rootProject.file("../default-keywords.json")) { rename { "content/default-keywords.json" } }
    from(rootProject.file("../default-rules.json")) { rename { "content/default-rules.json" } }
    into(layout.buildDirectory.dir("generated/shared-assets"))
}

val syncSharedIcon by tasks.registering(Copy::class) {
    from(rootProject.file("../icon.png")) { rename { "autoban_icon.png" } }
    into(layout.buildDirectory.dir("generated/shared-res/drawable"))
}

tasks.named("preBuild").configure {
    dependsOn(syncSharedAssets)
    dependsOn(syncSharedIcon)
}

dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    testImplementation("junit:junit:4.13.2")
}
