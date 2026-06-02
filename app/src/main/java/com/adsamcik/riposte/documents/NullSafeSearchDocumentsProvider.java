package com.adsamcik.riposte.documents;

import android.database.Cursor;
import android.os.Bundle;
import android.provider.DocumentsProvider;

import androidx.annotation.Nullable;

/**
 * Java intermediate that overrides {@link DocumentsProvider#querySearchDocuments(String, String[], Bundle)}
 * to gracefully accept a null {@code queryArgs} Bundle.
 *
 * <p>Required because Kotlin enforces parameter-level non-null checks on
 * overrides where the Java parent declares the parameter without a
 * {@code @Nullable} annotation — and the framework occasionally passes
 * null here (the legacy String-based ContentResolver.query route, plus
 * some test paths and adb shell content tools). A null Bundle from the
 * caller would trip Kotlin's runtime null check and crash the provider
 * before our search dispatch logic ever runs.
 *
 * <p>Kotlin subclasses override {@link #doQuerySearchDocuments} instead,
 * which receives a guaranteed non-null Bundle (defaulted to empty if the
 * upstream caller passed null).
 */
public abstract class NullSafeSearchDocumentsProvider extends DocumentsProvider {

    @Override
    public Cursor querySearchDocuments(
            String rootId,
            @Nullable String[] projection,
            @Nullable Bundle queryArgs
    ) {
        return doQuerySearchDocuments(
                rootId,
                projection,
                queryArgs != null ? queryArgs : Bundle.EMPTY
        );
    }

    /**
     * Subclass hook that receives a guaranteed non-null Bundle.
     */
    protected abstract Cursor doQuerySearchDocuments(
            String rootId,
            @Nullable String[] projection,
            Bundle queryArgs
    );
}

