plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.1"
}

group = "com.mariadbprofiler.plugin"
version = "0.1.0"

repositories {
    mavenCentral()
}

// Standalone configuration for deps to bundle into plugin JAR (not extending implementation
// to avoid pulling in IDE platform JARs that the intellij plugin adds transitively)
val bundledDeps: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    bundledDeps("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("junit:junit:4.13.2")
}

intellij {
    version.set("2023.3")
    type.set("IC") // IntelliJ Community - compatible with PhpStorm
    plugins.set(listOf())
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    jar {
        // Bundle implementation dependencies (kotlinx-serialization) into the plugin JAR
        from(provider {
            bundledDeps
                .filter { it.extension == "jar" }
                .map { zipTree(it) }
        }) {
            exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
            exclude("META-INF/*.xml")
            exclude("META-INF/versions/**")
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    patchPluginXml {
        sinceBuild.set("233")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    test {
        useJUnit()
    }
}
