import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.android.build.gradle.BaseExtension

// ! Bu depo @keyiflerolsun'un Kekik-cloudstream projesinden (GPL-3.0) türetilmiştir.
// ! Bakım: Ahmet

buildscript {
    repositories {
        google()
        mavenCentral()
        // Araçların ve bağımlılıkların çekildiği depo
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        // CloudStream'in gradle eklentisi: .cs3 dosyalarını üreten her şeyi yapan
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // GitHub Actions içinde derlenirken GITHUB_REPOSITORY otomatik gelir.
        // Local derlemede (bilgisayarında) bu yedek değer kullanılır.
        // TODO: Yayınlarken kendi GitHub adresinle değiştir: "https://github.com/KULLANICI_ADIN/cs-ahmet"
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/KULLANICI_ADIN/cs-ahmet")

        authors = listOf("Ahmet")
    }

    android {
        namespace = "com.keyiflerolsun" // orijinal paket adlarıyla uyum için korunuyor

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    listOf(
                        "-Xno-call-assertions",
                        "-Xno-param-assertions",
                        "-Xno-receiver-assertions"
                    )
                )
            }
        }
    }


    dependencies {
        val cloudstream by configurations
        val implementation by configurations

        // CloudStream sınıflarının stub'ları (derleme için)
        cloudstream("com.lagradost:cloudstream3:pre-release")

        // Uygulamanın eklediği bağımlılıklar — gerekmedikçe eklemene gerek yok
        implementation(kotlin("stdlib"))                                              // Kotlin'in temel kütüphanesi
        implementation("com.github.Blatzar:NiceHttp:0.4.13")                          // HTTP kütüphanesi
        implementation("org.jsoup:jsoup:1.19.1")                                      // HTML ayrıştırıcı
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")   // Kotlin için Jackson JSON kütüphanesi
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")          // JSON-nesne dönüştürme kütüphanesi
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")     // Kotlin için asenkron işlemler
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
        implementation("com.github.vidstige:jadb:v1.2.1")
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
