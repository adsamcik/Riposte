/**
 * Gradle build script for compiling the Rust native library (riposte-jni).
 *
 * Uses cargo-ndk to cross-compile for Android targets (arm64-v8a, x86_64).
 * The resulting .so files are placed in app/src/main/jniLibs/<abi>/.
 *
 * Prerequisites:
 * - Rust toolchain (rustup + cargo)
 * - cargo-ndk (`cargo install cargo-ndk`)
 * - Android NDK (set ANDROID_NDK_HOME or installed via SDK Manager)
 * - Rust targets: aarch64-linux-android, x86_64-linux-android
 *
 * Usage:
 *   ./gradlew buildRustNative          # Build for all targets (release)
 *   ./gradlew buildRustNativeDebug     # Build for all targets (debug)
 */

val nativeDir = rootProject.file("native")
val jniLibsDir = rootProject.file("app/src/main/jniLibs")

data class RustTarget(val abi: String, val triple: String, val ndkTriple: String)

val rustTargets = listOf(
    RustTarget("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android"),
    RustTarget("x86_64", "x86_64-linux-android", "x86_64-linux-android"),
)

fun findCargoNdk(): String {
    val home = System.getProperty("user.home")
    val candidates = listOf(
        "$home/.cargo/bin/cargo-ndk",
        "$home/.cargo/bin/cargo-ndk.exe",
    )
    return candidates.firstOrNull { file(it).exists() }
        ?: throw GradleException(
            "cargo-ndk not found. Install with: cargo install cargo-ndk"
        )
}

fun findNdkHome(): String {
    // Check environment variable first
    System.getenv("ANDROID_NDK_HOME")?.let { if (file(it).exists()) return it }

    // Check local.properties
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = java.util.Properties()
        localProps.inputStream().use { props.load(it) }
        props.getProperty("ndk.dir")?.let { if (file(it).exists()) return it }
    }

    // Check SDK Manager NDK installations
    val sdkDir = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: "${System.getProperty("user.home")}/AppData/Local/Android/Sdk"

    val ndkDir = file("$sdkDir/ndk")
    if (ndkDir.exists()) {
        val latest = ndkDir.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
        if (latest != null) return latest.absolutePath
    }

    throw GradleException(
        "Android NDK not found. Set ANDROID_NDK_HOME or install via SDK Manager."
    )
}

tasks.register("buildRustNative") {
    group = "rust"
    description = "Build Rust native library for all Android targets (release)"

    inputs.dir(nativeDir.resolve("riposte-core/src"))
    inputs.dir(nativeDir.resolve("riposte-jni/src"))
    inputs.file(nativeDir.resolve("Cargo.toml"))
    inputs.file(nativeDir.resolve("riposte-core/Cargo.toml"))
    inputs.file(nativeDir.resolve("riposte-jni/Cargo.toml"))

    outputs.dir(jniLibsDir)

    doLast {
        val cargoNdk = findCargoNdk()
        val ndkHome = findNdkHome()

        for (target in rustTargets) {
            val outputDir = jniLibsDir.resolve(target.abi)
            outputDir.mkdirs()

            exec {
                workingDir = nativeDir
                environment("ANDROID_NDK_HOME", ndkHome)
                commandLine(
                    cargoNdk,
                    "-t", target.triple,
                    "-o", outputDir.absolutePath,
                    "build",
                    "--release",
                    "-p", "riposte-jni",
                )
            }

            // Copy libc++_shared.so from NDK (required by USearch C++ dependency)
            val hostTag = when {
                org.gradle.internal.os.OperatingSystem.current().isWindows -> "windows-x86_64"
                org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "darwin-x86_64"
                else -> "linux-x86_64"
            }
            val libcppSrc = file(
                "$ndkHome/toolchains/llvm/prebuilt/$hostTag/sysroot/usr/lib/${target.ndkTriple}/libc++_shared.so"
            )
            if (libcppSrc.exists()) {
                libcppSrc.copyTo(outputDir.resolve("libc++_shared.so"), overwrite = true)
                logger.lifecycle("Bundled libc++_shared.so for ${target.abi}")
            } else {
                logger.warn("libc++_shared.so not found at: ${libcppSrc.path}")
            }

            logger.lifecycle("Built riposte-jni for ${target.abi}")
        }
    }
}

tasks.register("buildRustNativeDebug") {
    group = "rust"
    description = "Build Rust native library for all Android targets (debug)"

    doLast {
        val cargoNdk = findCargoNdk()
        val ndkHome = findNdkHome()

        for (target in rustTargets) {
            val outputDir = jniLibsDir.resolve(target.abi)
            outputDir.mkdirs()

            exec {
                workingDir = nativeDir
                environment("ANDROID_NDK_HOME", ndkHome)
                commandLine(
                    cargoNdk,
                    "-t", target.triple,
                    "-o", outputDir.absolutePath,
                    "build",
                    "-p", "riposte-jni",
                )
            }

            // Copy libc++_shared.so from NDK (required by USearch C++ dependency)
            val hostTag = when {
                org.gradle.internal.os.OperatingSystem.current().isWindows -> "windows-x86_64"
                org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "darwin-x86_64"
                else -> "linux-x86_64"
            }
            val libcppSrc = file(
                "$ndkHome/toolchains/llvm/prebuilt/$hostTag/sysroot/usr/lib/${target.ndkTriple}/libc++_shared.so"
            )
            if (libcppSrc.exists()) {
                libcppSrc.copyTo(outputDir.resolve("libc++_shared.so"), overwrite = true)
            }
        }
    }
}

tasks.register("cleanRustNative") {
    group = "rust"
    description = "Clean Rust build artifacts"

    doLast {
        // Clean cargo target
        exec {
            workingDir = nativeDir
            commandLine("cargo", "clean")
        }
        // Clean jniLibs
        for (target in rustTargets) {
            val soFile = jniLibsDir.resolve("${target.abi}/libriposte_jni.so")
            if (soFile.exists()) {
                soFile.delete()
                logger.lifecycle("Removed ${soFile.path}")
            }
        }
    }
}
