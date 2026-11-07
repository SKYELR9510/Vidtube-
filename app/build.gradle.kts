import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.silva.vidtube"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.silva.vidtube"
        minSdk = 26
        targetSdk = 35
        // En GitHub cada compilación sube el número de versión automáticamente
        val ciBuild = System.getenv("VIDTUBE_BUILD")?.toIntOrNull()
        versionCode = if (ciBuild != null) 100 + ciBuild else 4
        versionName = if (ciBuild != null) "1.3.$ciBuild" else "1.3"

        // Repositorio de GitHub donde la app busca actualizaciones (usuario/vidtube)
        val repo = System.getenv("GITHUB_REPOSITORY")
            ?: (project.findProperty("vidtubeRepo") as String?) ?: ""
        buildConfigField("String", "UPDATE_REPO", "\"$repo\"")
    }

    // Clave de firma fija: la misma en tu PC y en GitHub, para que las actualizaciones
    // se instalen encima sin desinstalar. Se lee de keystore.properties (PC) o de
    // variables de entorno (GitHub). Si no existe, se usa la clave de depuración.
    val keyProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val storeFilePath = System.getenv("VIDTUBE_KEYSTORE") ?: keyProps.getProperty("storeFile")
    val hasReleaseKey = storeFilePath != null && rootProject.file(storeFilePath).exists()
    signingConfigs {
        if (hasReleaseKey) {
            create("vidtube") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = System.getenv("VIDTUBE_STORE_PASSWORD") ?: keyProps.getProperty("storePassword")
                keyAlias = System.getenv("VIDTUBE_KEY_ALIAS") ?: keyProps.getProperty("keyAlias")
                keyPassword = System.getenv("VIDTUBE_KEY_PASSWORD") ?: keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Sin ofuscación: NewPipe Extractor usa reflexión (Rhino) y se rompe con R8
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("vidtube") ?: signingConfigs.getByName("debug")
        }
        debug {
            // Así lo que compilas en tu PC también se puede instalar encima de lo de GitHub
            signingConfigs.findByName("vidtube")?.let { signingConfig = it }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.md",
                "META-INF/CHANGES",
                "META-INF/COPYRIGHT",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }
}

dependencies {
    // Motor que obtiene los videos de YouTube sin anuncios (misma versión que usa NewPipe)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.0")
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // Red
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.reactivex.rxjava3:rxjava:3.1.12")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("androidx.webkit:webkit:1.12.1")

    // Reproductor
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-dash:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    // Controles del Bluetooth del carro, auriculares y pantalla de bloqueo
    implementation("com.google.android.exoplayer:extension-mediasession:2.19.1")

    // Interfaz
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
