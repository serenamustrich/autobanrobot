plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autobanrobot.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autobanrobot.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    testImplementation("junit:junit:4.13.2")
}
