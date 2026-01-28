plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mememymood.core.ml"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // WorkManager with Hilt
    implementation(libs.workmanager.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // DataStore for version tracking
    implementation(libs.datastore.preferences)

    // ML Kit
    implementation(libs.bundles.mlkit)

    // MediaPipe (for semantic text embeddings - legacy USE model)
    implementation(libs.mediapipe.tasks.text)

    // Google AI Edge RAG SDK (for EmbeddingGemma)
    implementation(libs.ai.edge.rag)

    // LiteRT (for semantic embeddings - replaces TensorFlow Lite)
    implementation(libs.bundles.litert)

    // DJL HuggingFace Tokenizers (for EmbeddingGemma tokenization)
    implementation(libs.djl.tokenizers)

    // ExifInterface for metadata handling
    implementation(libs.exifinterface)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.workmanager.testing)
}
