package com.nuvio.app.features.downloads

/** A folder picked via the system folder picker. */
internal data class LocalMediaFolderPick(
    val uri: String,
    val displayName: String?,
)

/**
 * Platform side of Local Media: folder scanning + the system pickers.
 * Android scans SAF tree folders and opens the system file picker; other
 * platforms report [isSupported] = false and the UI shows a not-supported state.
 */
internal expect object LocalMediaPlatform {
    val isSupported: Boolean

    suspend fun scanFolder(folderUri: String): List<LocalMediaFile>

    fun pickFolder(onResult: (Result<LocalMediaFolderPick?>) -> Unit)

    fun pickVideoFile(onResult: (Result<LocalMediaFile?>) -> Unit)
}
