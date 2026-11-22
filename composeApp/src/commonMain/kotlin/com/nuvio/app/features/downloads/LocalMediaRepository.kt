package com.nuvio.app.features.downloads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local Media (Downloads tab): folders the user added, scanned on device and
 * grouped into series. Folders + the last scan are persisted so the tab shows
 * instantly; [refresh] re-scans the folders.
 */
internal object LocalMediaRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(LocalMediaUiState())
    val uiState: StateFlow<LocalMediaUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var scanJob: Job? = null

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        runCatching { LocalMediaStorage.loadPayload() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { payload -> runCatching { json.decodeFromString<LocalMediaPayload>(payload) }.getOrNull() }
            ?.let { payload -> _uiState.value = payload.toUiState() }
        refresh()
    }

    fun addFolder(pick: LocalMediaFolderPick) {
        val folders = (_uiState.value.folders + LocalMediaFolder(uri = pick.uri, displayName = pick.displayName))
            .distinctBy { it.uri }
        _uiState.update { it.copy(folders = folders, hasLoadedFolders = true) }
        persist()
        refresh()
    }

    fun removeFolder(folderUri: String) {
        _uiState.update { state ->
            val folders = state.folders.filterNot { it.uri == folderUri }
            val files = state.series.flatMap { it.files }.filterNot { it.folderUri == folderUri }
            state.copy(folders = folders, series = files.toLocalMediaSeries())
        }
        persist()
    }

    fun refresh() {
        if (!LocalMediaPlatform.isSupported) return
        scanJob?.cancel()
        scanJob = scope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val folders = _uiState.value.folders
            val files = folders.flatMap { folder ->
                runCatching { LocalMediaPlatform.scanFolder(folder.uri) }.getOrElse { emptyList() }
            }
            _uiState.update { it.copy(series = files.toLocalMediaSeries(), isScanning = false) }
            persist()
        }
    }

    fun pickFolder(onResult: (Result<LocalMediaFolderPick?>) -> Unit) {
        LocalMediaPlatform.pickFolder(onResult)
    }

    fun pickVideoFile(onResult: (Result<LocalMediaFile?>) -> Unit) {
        LocalMediaPlatform.pickVideoFile(onResult)
    }

    private fun persist() {
        runCatching {
            LocalMediaStorage.savePayload(json.encodeToString(_uiState.value.toPayload()))
        }
    }
}
