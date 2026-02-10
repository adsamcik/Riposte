package com.adsamcik.riposte.core.common.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the app is in the foreground or background using [ProcessLifecycleOwner].
 * Call [init] from [android.app.Application.onCreate] to start observing.
 */
@Singleton
class AppLifecycleTracker @Inject constructor() : DefaultLifecycleObserver {

    private val _isInBackground = MutableStateFlow(false)

    /** Emits `true` when the app moves to the background, `false` when it returns. */
    val isInBackground: StateFlow<Boolean> = _isInBackground.asStateFlow()

    /** Must be called once from Application.onCreate(). */
    fun init() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        _isInBackground.value = false
    }

    override fun onStop(owner: LifecycleOwner) {
        _isInBackground.value = true
    }
}
