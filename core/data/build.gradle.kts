import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glykos.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    api(project(":core:domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Settings + Keystore-encrypted credentials. Never log or hardcode secrets.
    api(libs.androidx.datastore.preferences)

    testImplementation(libs.ktor.client.mock)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Live test against the real LibreLinkUp API, opt-in only:
//   ./gradlew :core:data:testDebugUnitTest -Pllu.live=true --tests '*LibreLinkUpLiveTest*'
// Credentials come from local.properties (git-ignored) and are passed only to the test JVM.
tasks.withType<Test>().configureEach {
    if (providers.gradleProperty("llu.live").orNull == "true") {
        val props = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(props::load)
        systemProperty("llu.live", "true")
        listOf("llu.email", "llu.password", "llu.host").forEach { key ->
            props.getProperty(key)?.let { systemProperty(key, it) }
        }
        outputs.upToDateWhen { false }
        testLogging { showStandardStreams = true }
    }
}
