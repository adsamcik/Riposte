package com.adsamcik.riposte.core.common.crash

import javax.inject.Qualifier

/** Qualifier for the [java.io.File] directory that stores crash reports. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CrashLogDir
