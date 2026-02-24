//! JNI bridge for riposte-core.
//!
//! Exposes the Rust tokenizer and vector index to Kotlin via JNI. Handles are
//! stored as `jlong` pointers on the Kotlin side, following standard JNI
//! ownership patterns.

use std::sync::Arc;

use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JObject, JString};
use jni::sys::{jboolean, jfloatArray, jint, jintArray, jlong, jobjectArray};

use riposte_core::tokenizer::SentencePieceTokenizer;
use riposte_core::vector_index::VectorIndex;

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

// ---------------------------------------------------------------------------
// VectorIndex JNI bridge
// ---------------------------------------------------------------------------

/// Creates a new vector index. Returns a native handle.
///
/// Kotlin signature:
/// ```kotlin
/// external fun nativeCreate(dimensions: Int, useF16: Boolean): Long
/// ```
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeCreate(
    mut env: JNIEnv,
    _class: JClass,
    dimensions: jint,
    use_f16: jboolean,
) -> jlong {
    let result = (|| -> Result<jlong, String> {
        let index = VectorIndex::with_quantization(dimensions as usize, use_f16 != 0)?;
        let arc = Arc::new(index);
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

/// Reserves capacity in the index.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeReserve(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    capacity: jint,
) {
    let result = (|| -> Result<(), String> {
        if handle == 0 {
            return Err("Null vector index handle".into());
        }
        let index = unsafe { &*(handle as *const VectorIndex) };
        index.reserve(capacity as usize)
    })();

    if let Err(msg) = result {
        let _ = env.throw_new("java/lang/RuntimeException", msg);
    }
}

/// Adds a vector with the given key.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeAdd(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    key: jlong,
    vector: jfloatArray,
) {
    let result = (|| -> Result<(), String> {
        if handle == 0 {
            return Err("Null vector index handle".into());
        }
        let index = unsafe { &*(handle as *const VectorIndex) };

        let vec_obj = unsafe { jni::objects::JFloatArray::from_raw(vector) };
        let len = env
            .get_array_length(&vec_obj)
            .map_err(|e| format!("Failed to get array length: {e}"))? as usize;
        let mut buf = vec![0.0f32; len];
        env.get_float_array_region(&vec_obj, 0, &mut buf)
            .map_err(|e| format!("Failed to read float array: {e}"))?;

        index.add(key as u64, &buf)
    })();

    if let Err(msg) = result {
        let _ = env.throw_new("java/lang/RuntimeException", msg);
    }
}

/// Searches for nearest neighbors. Returns `jobjectArray` of length 2:
/// element 0 = `jlongArray` of keys, element 1 = `jfloatArray` of distances.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeSearch(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    query: jfloatArray,
    count: jint,
) -> jobjectArray {
    let result = (|| -> Result<jobjectArray, String> {
        if handle == 0 {
            return Err("Null vector index handle".into());
        }
        let index = unsafe { &*(handle as *const VectorIndex) };

        let query_obj = unsafe { jni::objects::JFloatArray::from_raw(query) };
        let len = env
            .get_array_length(&query_obj)
            .map_err(|e| format!("Failed to get array length: {e}"))? as usize;
        let mut buf = vec![0.0f32; len];
        env.get_float_array_region(&query_obj, 0, &mut buf)
            .map_err(|e| format!("Failed to read float array: {e}"))?;

        let (keys, distances) = index.search(&buf, count as usize)?;

        // Build jlongArray of keys
        let key_arr = env
            .new_long_array(keys.len() as i32)
            .map_err(|e| format!("Failed to create long array: {e}"))?;
        let jlong_keys: Vec<jlong> = keys.iter().map(|&k| k as jlong).collect();
        env.set_long_array_region(&key_arr, 0, &jlong_keys)
            .map_err(|e| format!("Failed to set long array region: {e}"))?;

        // Build jfloatArray of distances
        let dist_arr = env
            .new_float_array(distances.len() as i32)
            .map_err(|e| format!("Failed to create float array: {e}"))?;
        env.set_float_array_region(&dist_arr, 0, &distances)
            .map_err(|e| format!("Failed to set float array region: {e}"))?;

        // Wrap into Object[2]
        let object_class = env
            .find_class("java/lang/Object")
            .map_err(|e| format!("Failed to find Object class: {e}"))?;
        let outer = env
            .new_object_array(2, &object_class, &JObject::null())
            .map_err(|e| format!("Failed to create object array: {e}"))?;

        let key_obj = unsafe { JObject::from_raw(key_arr.into_raw()) };
        let dist_obj = unsafe { JObject::from_raw(dist_arr.into_raw()) };
        env.set_object_array_element(&outer, 0, &key_obj)
            .map_err(|e| format!("Failed to set keys element: {e}"))?;
        env.set_object_array_element(&outer, 1, &dist_obj)
            .map_err(|e| format!("Failed to set distances element: {e}"))?;

        Ok(outer.into_raw())
    })();

    match result {
        Ok(arr) => arr,
        Err(msg) => {
            let _ = env.throw_new("java/lang/RuntimeException", msg);
            std::ptr::null_mut()
        }
    }
}

/// Removes a vector by key.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeRemove(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    key: jlong,
) -> jboolean {
    let result = (|| -> Result<bool, String> {
        if handle == 0 {
            return Err("Null vector index handle".into());
        }
        let index = unsafe { &*(handle as *const VectorIndex) };
        index.remove(key as u64)
    })();

    match result {
        Ok(found) => found as jboolean,
        Err(msg) => {
            let _ = env.throw_new("java/lang/RuntimeException", msg);
            0
        }
    }
}

/// Returns the number of vectors in the index.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeLen(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    if handle == 0 {
        let _ = env.throw_new("java/lang/RuntimeException", "Null vector index handle");
        return 0;
    }
    let index = unsafe { &*(handle as *const VectorIndex) };
    index.len() as jint
}

/// Saves the index to a file path.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeSave(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    path: JString,
) {
    let result = (|| -> Result<(), String> {
        if handle == 0 {
            return Err("Null vector index handle".into());
        }
        let index = unsafe { &*(handle as *const VectorIndex) };
        let path_str: String = env
            .get_string(&path)
            .map_err(|e| format!("Failed to get string: {e}"))?
            .into();
        index.save(&path_str)
    })();

    if let Err(msg) = result {
        let _ = env.throw_new("java/lang/RuntimeException", msg);
    }
}

/// Loads an index from a file path.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeLoad(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    path: JString,
) {
    let result = (|| -> Result<(), String> {
        if handle == 0 {
            return Err("Null vector index handle".into());
        }
        let index = unsafe { &*(handle as *const VectorIndex) };
        let path_str: String = env
            .get_string(&path)
            .map_err(|e| format!("Failed to get string: {e}"))?
            .into();
        index.load(&path_str)
    })();

    if let Err(msg) = result {
        let _ = env.throw_new("java/lang/RuntimeException", msg);
    }
}

/// Checks if a key exists in the index.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeContains(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    key: jlong,
) -> jboolean {
    if handle == 0 {
        let _ = env.throw_new("java/lang/RuntimeException", "Null vector index handle");
        return 0;
    }
    let index = unsafe { &*(handle as *const VectorIndex) };
    index.contains(key as u64) as jboolean
}

/// Releases the native vector index handle.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adsamcik_riposte_core_ml_RustVectorIndex_nativeRelease(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Arc::from_raw(handle as *const VectorIndex);
        }
    }
}
