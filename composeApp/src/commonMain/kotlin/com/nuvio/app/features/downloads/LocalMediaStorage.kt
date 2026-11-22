package com.nuvio.app.features.downloads

/** Persists the Local Media folders + last scan (JSON payload). */
internal expect object LocalMediaStorage {
    fun loadPayload(): String?

    fun savePayload(payload: String)
}
