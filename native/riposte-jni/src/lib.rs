//! JNI bridge for riposte-core.
//!
//! Exposes the Rust tokenizer to Kotlin via JNI. The tokenizer handle is
//! stored as a `jlong` pointer on the Kotlin side, following standard JNI
//! ownership patterns.

use std::sync::Arc;

use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jint, jintArray, jlong};

use riposte_core::tokenizer::SentencePieceTokenizer;

/// Parses a SentencePiece `.model` file from raw bytes and returns a native handle.
///
/// Kotlin signature:
/// ```kotlin
/// external fun nativeParse(modelData: ByteArray): Long
/// ```
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustTokenizer_nativeParse(
    mut env: JNIEnv,
    _class: JClass,
    model_data: JByteArray,
) -> jlong {
    let result = (|| -> Result<jlong, String> {
        let data = env
            .convert_byte_array(&model_data)
            .map_err(|e| format!("Failed to read byte array: {e}"))?;

        let tokenizer = SentencePieceTokenizer::from_model_data(&data)?;
        let arc = Arc::new(tokenizer);
        Ok(Arc::into_raw(arc) as jlong)
    })();

    match result {
        Ok(handle) => handle,
        Err(msg) => {
            let _ = env.throw_new("java/lang/RuntimeException", msg);
            0
        }
    }
}

/// Encodes text into token IDs using the native tokenizer.
///
/// Kotlin signature:
/// ```kotlin
/// external fun nativeEncode(handle: Long, text: String): IntArray
/// ```
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustTokenizer_nativeEncode(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    text: JString,
) -> jintArray {
    let result = (|| -> Result<jintArray, String> {
        if handle == 0 {
            return Err("Null tokenizer handle".into());
        }

        let tokenizer = unsafe { &*(handle as *const SentencePieceTokenizer) };
        let text_str: String = env
            .get_string(&text)
            .map_err(|e| format!("Failed to get string: {e}"))?
            .into();

        let token_ids = tokenizer.encode(&text_str);

        // Convert Vec<u32> → jintArray (i32)
        let jint_ids: Vec<jint> = token_ids.iter().map(|&id| id as jint).collect();
        let arr = env
            .new_int_array(jint_ids.len() as i32)
            .map_err(|e| format!("Failed to create int array: {e}"))?;
        env.set_int_array_region(&arr, 0, &jint_ids)
            .map_err(|e| format!("Failed to set int array region: {e}"))?;

        Ok(arr.into_raw())
    })();

    match result {
        Ok(arr) => arr,
        Err(msg) => {
            let _ = env.throw_new("java/lang/RuntimeException", msg);
            std::ptr::null_mut()
        }
    }
}

/// Returns the vocabulary size of the tokenizer.
///
/// Kotlin signature:
/// ```kotlin
/// external fun nativeVocabSize(handle: Long): Int
/// ```
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustTokenizer_nativeVocabSize(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    if handle == 0 {
        return 0;
    }
    let tokenizer = unsafe { &*(handle as *const SentencePieceTokenizer) };
    tokenizer.vocab_size() as jint
}

/// Releases the native tokenizer handle.
///
/// Kotlin signature:
/// ```kotlin
/// external fun nativeRelease(handle: Long)
/// ```
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustTokenizer_nativeRelease(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        // Reconstruct Arc and drop it — decrements refcount, frees when zero
        unsafe {
            let _ = Arc::from_raw(handle as *const SentencePieceTokenizer);
        }
    }
}
