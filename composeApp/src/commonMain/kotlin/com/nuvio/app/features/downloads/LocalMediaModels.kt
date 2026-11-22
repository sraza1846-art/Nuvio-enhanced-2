package com.nuvio.app.features.downloads

import kotlinx.serialization.Serializable

/** A folder the user added from the Downloads tab (Local Media). */
internal data class LocalMediaFolder(
    val uri: String,
    val displayName: String?,
)

/** One video file found inside an added folder (or picked via Open Video). */
internal data class LocalMediaFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val folderUri: String,
    val seriesName: String,
)

/** Files grouped under one show/folder name for the Local Media list. */
internal data class LocalMediaSeries(
    val name: String,
    val files: List<LocalMediaFile>,
)

internal data class LocalMediaUiState(
    val folders: List<LocalMediaFolder> = emptyList(),
    val series: List<LocalMediaSeries> = emptyList(),
    val isScanning: Boolean = false,
    val hasLoadedFolders: Boolean = false,
)

internal fun List<LocalMediaFile>.toLocalMediaSeries(): List<LocalMediaSeries> =
    groupBy { it.seriesName }
        .map { (name, files) ->
            LocalMediaSeries(
                name = name,
                files = files.sortedBy { it.name.lowercase() },
            )
        }
        .sortedBy { it.name.lowercase() }

@Serializable
internal data class LocalMediaPayload(
    val folders: List<LocalMediaFolderPayload> = emptyList(),
    val files: List<LocalMediaFilePayload> = emptyList(),
)

@Serializable
internal data class LocalMediaFolderPayload(
    val uri: String,
    val displayName: String? = null,
)

@Serializable
internal data class LocalMediaFilePayload(
    val uri: String,
    val name: String,
    val sizeBytes: Long = 0L,
    val folderUri: String,
    val seriesName: String,
)

internal fun LocalMediaUiState.toPayload(): LocalMediaPayload = LocalMediaPayload(
    folders = folders.map { LocalMediaFolderPayload(uri = it.uri, displayName = it.displayName) },
    files = series.flatMap { it.files }.map {
        LocalMediaFilePayload(
            uri = it.uri,
            name = it.name,
            sizeBytes = it.sizeBytes,
            folderUri = it.folderUri,
            seriesName = it.seriesName,
        )
    },
)

internal fun LocalMediaPayload.toUiState(): LocalMediaUiState = LocalMediaUiState(
    folders = folders.map { LocalMediaFolder(uri = it.uri, displayName = it.displayName) },
    series = files.map {
        LocalMediaFile(
            uri = it.uri,
            name = it.name,
            sizeBytes = it.sizeBytes,
            folderUri = it.folderUri,
            seriesName = it.seriesName,
        )
    }.toLocalMediaSeries(),
    hasLoadedFolders = true,
)
