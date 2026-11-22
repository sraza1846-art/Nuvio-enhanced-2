package com.nuvio.app.features.downloads

/** Local Media is Android-only: iOS reports unsupported and the tab shows a notice. */
internal actual object LocalMediaPlatform {
    actual val isSupported: Boolean = false

    actual suspend fun scanFolder(folderUri: String): List<LocalMediaFile> = emptyList()

    actual fun pickFolder(onResult: (Result<LocalMediaFolderPick?>) -> Unit) {
        onResult(Result.failure(IllegalStateException("Local media is not supported on this device.")))
    }

    actual fun pickVideoFile(onResult: (Result<LocalMediaFile?>) -> Unit) {
        onResult(Result.failure(IllegalStateException("Local media is not supported on this device.")))
    }
}
