import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
    .orElse(providers.environmentVariable("SUPABASE_URL"))
    .orElse(localProperties.getProperty("SUPABASE_URL", ""))
    .get()
val supabaseAnonKey = providers.gradleProperty("SUPABASE_ANON_KEY")
    .orElse(providers.environmentVariable("SUPABASE_ANON_KEY"))
    .orElse(localProperties.getProperty("SUPABASE_ANON_KEY", ""))
    .get()
val catalogUrl = providers.gradleProperty("RETROBOY_CATALOG_URL")
    .orElse(providers.environmentVariable("RETROBOY_CATALOG_URL"))
    .orElse(localProperties.getProperty("RETROBOY_CATALOG_URL", ""))
    .get()

android {
    namespace = "com.retroboy.arcade"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.retroboy.arcade"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseAnonKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "CATALOG_URL", "\"${catalogUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val unpackRetroboyFonts = tasks.register<org.gradle.api.tasks.Copy>("unpackRetroboyFonts") {
    from(zipTree(rootProject.file("artwork/retroboy-fonts.zip")))
    into(layout.buildDirectory.dir("generated/retroboyFonts/res"))
}
android.sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/retroboyFonts/res"))
tasks.named("preBuild").configure { dependsOn(unpackRetroboyFonts) }

val unpackRetroboyProfileAssets = tasks.register<org.gradle.api.tasks.Copy>("unpackRetroboyProfileAssets") {
    from(zipTree(rootProject.file("artwork/retroboy-profile-assets.zip")))
    into(layout.buildDirectory.dir("generated/retroboyProfileAssets/res"))
}
android.sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/retroboyProfileAssets/res"))
tasks.named("preBuild").configure { dependsOn(unpackRetroboyProfileAssets) }
