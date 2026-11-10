import java.util.Properties
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

val useInstaPackage: Boolean = run {
    val cliFlag = project.findProperty("useInstaPackage") as? String
    if (cliFlag != null) return@run cliFlag.toBoolean()

    val targetPkg = project.findProperty("targetPackage") as? String
    if (targetPkg != null) return@run (targetPkg == "insta")

    val envFlag = System.getenv("FOCUSGRAM_USE_INSTA_PACKAGE")
    if (envFlag != null) return@run envFlag.toBoolean()

    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        val p = Properties()
        localPropsFile.inputStream().use { p.load(it) }
        p.getProperty("focusgram.use_insta_package")?.toBoolean() ?: false
    } else {
        false
    }
}

val targetAppId = if (useInstaPackage) "com.instagram.android" else "com.ujwal.focusgram"
val targetAppName = if (useInstaPackage) "Instagram" else "FocusGram"
val apkPrefix = if (useInstaPackage) "FocusGram-uncloned-insta" else "FocusGram"

android {
    namespace = "com.ujwal.focusgram"
    compileSdk = 35

    defaultConfig {
        applicationId = targetAppId
        minSdk = 26
        targetSdk = 35
        versionCode = 300
        versionName = "3.0.0"

        manifestPlaceholders["appLabel"] = targetAppName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    androidResources {
        localeFilters += listOf("en")
    }


    val keystorePropertiesFile = rootProject.file("key.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }

    val hasReleaseSigning = keystorePropertiesFile.exists() &&
            keystoreProperties.containsKey("storeFile") &&
            keystoreProperties.containsKey("storePassword") &&
            keystoreProperties.containsKey("keyAlias") &&
            keystoreProperties.containsKey("keyPassword")

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val storePath = keystoreProperties.getProperty("storeFile")
                val storeF = File(storePath)
                storeFile = if (storeF.isAbsolute) storeF else (rootProject.file(storePath).takeIf { it.exists() } ?: project.file(storePath))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else if (project.findProperty("allowDebugSigning")?.toString()?.toBoolean() == true) {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/*.properties",
                "/kotlin/**",
                "/kotlin-tooling-metadata.json"
            )
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += listOf(
                "**/libclearkey.so"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    val abiVersionCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val abi = output.filters.find {
                    it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
                }?.identifier
                val suffix = abiVersionCodes[abi]
                if (suffix != null) {
                    output.versionCode.set((defaultConfig.versionCode ?: 300) * 10 + suffix)
                }
            }
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (output != null) {
                val abi = output.getFilter("ABI") ?: "universal"
                output.outputFileName = "$apkPrefix-v${variant.versionName}-$abi-${variant.buildType.name}.apk"
            }
        }
    }
}

dependencies {
    // AndroidX & Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose & Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // GeckoView Engine
    implementation(libs.geckoview.omni) {
        // Exclude proprietary Google Play Services FIDO / GMS libraries for F-Droid compliance
        exclude(group = "com.google.android.gms")
    }

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Jetpack DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Security & Biometric
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing & Debug
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

configurations.all {
    exclude(group = "com.google.android.gms")
    resolutionStrategy {
        force(
            "androidx.lifecycle:lifecycle-common:2.8.7",
            "androidx.lifecycle:lifecycle-process:2.8.7",
            "androidx.lifecycle:lifecycle-runtime:2.8.7",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7",
            "androidx.lifecycle:lifecycle-runtime-compose:2.8.7",
            "androidx.lifecycle:lifecycle-viewmodel:2.8.7",
            "androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7",
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7",
            "androidx.lifecycle:lifecycle-livedata:2.8.7",
            "androidx.lifecycle:lifecycle-livedata-core:2.8.7",
            "androidx.lifecycle:lifecycle-livedata-core-ktx:2.8.7",
            "androidx.lifecycle:lifecycle-common-java8:2.8.7",
            "androidx.core:core:1.15.0",
            "androidx.core:core-ktx:1.15.0",
            "androidx.fragment:fragment:1.8.6",
            "androidx.fragment:fragment-ktx:1.8.6",
            "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21",
            "org.jetbrains.kotlin:kotlin-reflect:2.0.21"
        )
    }
}

tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}

val stripGeckoViewNonFreeClasses by tasks.registering {
    description = "Strips proprietary WebAuthn/GMS classes from GeckoView artifacts for F-Droid compliance"
    doLast {
        val searchDirs = listOf(
            gradle.gradleUserHomeDir.resolve("caches"),
            rootProject.layout.buildDirectory.asFile.get(),
            project.layout.buildDirectory.asFile.get()
        )
        for (baseDir in searchDirs) {
            if (!baseDir.exists()) continue
            baseDir.walkTopDown()
                .filter { it.isFile && it.name.startsWith("geckoview-omni") && (it.name.endsWith(".jar") || it.name.endsWith(".aar")) }
                .forEach { file ->
                    try {
                        val uri = URI.create("jar:" + file.toURI())
                        FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { fs ->
                            val classPath = fs.getPath("org/mozilla/geckoview/WebAuthnTokenManager.class")
                            if (Files.exists(classPath)) {
                                Files.delete(classPath)
                                logger.lifecycle("FocusGram: Stripped WebAuthnTokenManager from ${file.name}")
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
        }
    }
}

tasks.matching { it.name.startsWith("minify") && it.name.endsWith("WithR8") }.configureEach {
    dependsOn(stripGeckoViewNonFreeClasses)
}

