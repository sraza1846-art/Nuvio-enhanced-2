package com.nuvio.app.features.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioTypeScale
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.compose_settings_root_downloads_title
import nuvio.composeapp.generated.resources.downloads_add_folder
import nuvio.composeapp.generated.resources.downloads_all_folders
import nuvio.composeapp.generated.resources.downloads_empty_message
import nuvio.composeapp.generated.resources.downloads_empty_title
import nuvio.composeapp.generated.resources.downloads_episodes
import nuvio.composeapp.generated.resources.downloads_local_media_empty_message
import nuvio.composeapp.generated.resources.downloads_local_media_empty_title
import nuvio.composeapp.generated.resources.downloads_local_media_pick_failed
import nuvio.composeapp.generated.resources.downloads_local_media_scanning
import nuvio.composeapp.generated.resources.downloads_local_media_unsupported
import nuvio.composeapp.generated.resources.downloads_local_media_unsupported_message
import nuvio.composeapp.generated.resources.downloads_open_video
import nuvio.composeapp.generated.resources.downloads_refresh
import nuvio.composeapp.generated.resources.downloads_remove_folder
import nuvio.composeapp.generated.resources.downloads_tab_all
import nuvio.composeapp.generated.resources.downloads_tab_app_downloads
import nuvio.composeapp.generated.resources.downloads_tab_local_media
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal enum class DownloadsTab(val key: String, val labelRes: StringResource) {
    All("all", Res.string.downloads_tab_all),
    AppDownloads("app", Res.string.downloads_tab_app_downloads),
    LocalMedia("local", Res.string.downloads_tab_local_media),
}

/**
 * Downloads bottom-tab screen: All / App Downloads / Local Media.
 * App Downloads reuses the existing downloads list content; Local Media shows
 * the folders the user added, scanned on device and grouped into series.
 */
@Composable
internal fun DownloadsTabScreen(
    modifier: Modifier = Modifier,
    onOpenDownload: (DownloadItem) -> Unit,
    onOpenDownloadDetails: (DownloadItem) -> Unit,
    onOpenLocalFile: (LocalMediaFile) -> Unit,
) {
    val downloadsUiState by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchProgressState by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val localMediaState by remember {
        LocalMediaRepository.ensureLoaded()
        LocalMediaRepository.uiState
    }.collectAsStateWithLifecycle()

    var selectedTabKey by rememberSaveable { mutableStateOf(DownloadsTab.All.key) }
    val selectedTab = DownloadsTab.entries.firstOrNull { it.key == selectedTabKey } ?: DownloadsTab.All
    var selectedShowId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFolderUri by rememberSaveable { mutableStateOf<String?>(null) }
    var actionsItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val speeds = rememberDownloadSpeeds(downloadsUiState.items)
    val watchStatusOf = remember(watchProgressState, watchedState) {
        { item: DownloadItem -> item.watchStatus(watchProgressState) }
    }
    val selectedShowTitle = remember(selectedShowId, downloadsUiState.items) {
        selectedShowId?.let { showId ->
            downloadsUiState.items.firstOrNull { it.isEpisode && it.parentMetaId == showId }?.title
        }
    }
    val onOpenActions: (DownloadItem) -> Unit = { actionsItemId = it.id }

    val localMediaSupported = LocalMediaPlatform.isSupported
    val addFolderText = stringResource(Res.string.downloads_add_folder)
    val openVideoText = stringResource(Res.string.downloads_open_video)
    val refreshText = stringResource(Res.string.downloads_refresh)
    val pickFailedText = stringResource(Res.string.downloads_local_media_pick_failed)

    fun pickFolder() {
        LocalMediaRepository.pickFolder { result ->
            result
                .onSuccess { pick -> if (pick != null) LocalMediaRepository.addFolder(pick) }
                .onFailure { NuvioToastController.show(pickFailedText) }
        }
    }

    fun pickVideo() {
        LocalMediaRepository.pickVideoFile { result ->
            result
                .onSuccess { file -> if (file != null) onOpenLocalFile(file) }
                .onFailure { NuvioToastController.show(pickFailedText) }
        }
    }

    PlatformBackHandler(enabled = selectedShowId != null) {
        selectedShowId = null
    }

    NuvioScreen(modifier = modifier) {
        stickyHeader {
            Column(modifier = Modifier.fillMaxWidth()) {
                NuvioScreenHeader(
                    title = selectedShowTitle
                        ?: stringResource(Res.string.compose_settings_root_downloads_title),
                    actions = {
                        if (localMediaSupported) {
                            IconButton(onClick = ::pickFolder) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = addFolderText,
                                    tint = MaterialTheme.nuvio.colors.textPrimary,
                                )
                            }
                            IconButton(onClick = ::pickVideo) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = openVideoText,
                                    tint = MaterialTheme.nuvio.colors.textPrimary,
                                )
                            }
                            IconButton(onClick = { LocalMediaRepository.refresh() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = refreshText,
                                    tint = MaterialTheme.nuvio.colors.textPrimary,
                                )
                            }
                        }
                    },
                )
                DownloadsTabPillRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTabKey = it.key },
                )
            }
        }

        val showId = selectedShowId
        when {
            showId != null && selectedTab != DownloadsTab.LocalMedia -> {
                downloadsShowContent(
                    episodes = downloadsUiState.items
                        .filter { it.isEpisode && it.parentMetaId == showId }
                        .sortedForSeriesDownloads(),
                    speeds = speeds,
                    watchStatusOf = watchStatusOf,
                    onPlay = onOpenDownload,
                    onOpenActions = onOpenActions,
                )
            }

            selectedTab == DownloadsTab.AppDownloads -> {
                downloadsRootContent(
                    items = downloadsUiState.items,
                    speeds = speeds,
                    watchStatusOf = watchStatusOf,
                    onPlay = onOpenDownload,
                    onOpenShow = { item -> selectedShowId = item.parentMetaId },
                    onOpenActions = onOpenActions,
                )
            }

            selectedTab == DownloadsTab.LocalMedia -> {
                localMediaTabContent(
                    state = localMediaState,
                    selectedFolderUri = selectedFolderUri,
                    onFolderSelected = { selectedFolderUri = it },
                    onRemoveFolder = { LocalMediaRepository.removeFolder(it) },
                    onPlay = onOpenLocalFile,
                    showEmptyState = true,
                    showFolderChips = true,
                )
            }

            else -> {
                // All: app downloads first, then local media. One shared empty
                // state when both sides are empty.
                if (downloadsUiState.items.isEmpty() && localMediaState.series.isEmpty()) {
                    item(key = "downloads_all_empty") {
                        DownloadsEmptyState(
                            title = stringResource(Res.string.downloads_empty_title),
                            message = stringResource(Res.string.downloads_empty_message),
                        )
                    }
                } else {
                    if (downloadsUiState.items.isNotEmpty()) {
                        downloadsRootContent(
                            items = downloadsUiState.items,
                            speeds = speeds,
                            watchStatusOf = watchStatusOf,
                            onPlay = onOpenDownload,
                            onOpenShow = { item -> selectedShowId = item.parentMetaId },
                            onOpenActions = onOpenActions,
                        )
                    }
                    localMediaTabContent(
                        state = localMediaState,
                        selectedFolderUri = selectedFolderUri,
                        onFolderSelected = { selectedFolderUri = it },
                        onRemoveFolder = { LocalMediaRepository.removeFolder(it) },
                        onPlay = onOpenLocalFile,
                        showEmptyState = false,
                        showFolderChips = false,
                    )
                }
            }
        }
    }

    val actionsItem = actionsItemId?.let { id -> downloadsUiState.items.firstOrNull { it.id == id } }
    DownloadActionSheet(
        item = actionsItem,
        watchStatus = actionsItem?.let(watchStatusOf) ?: DownloadWatchStatus(),
        onDismiss = { actionsItemId = null },
        onPlay = { actionsItem?.let(onOpenDownload) },
        onToggleTransfer = { actionsItem?.toggleTransfer() },
        onOpenDetails = { actionsItem?.let(onOpenDownloadDetails) },
        onDelete = { deleteItemId = actionsItem?.id },
    )

    val deleteItem = deleteItemId?.let { id -> downloadsUiState.items.firstOrNull { it.id == id } }
    if (deleteItem != null) {
        DeleteDownloadDialog(
            item = deleteItem,
            onConfirm = {
                DownloadsRepository.cancelDownload(deleteItem.id)
                deleteItemId = null
            },
            onDismiss = { deleteItemId = null },
        )
    }
}

@Composable
private fun DownloadsTabPillRow(
    selectedTab: DownloadsTab,
    onTabSelected: (DownloadsTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NuvioTokens.Space.s8),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        DownloadsTab.entries.forEach { tab ->
            DownloadsTabPill(
                selected = tab == selectedTab,
                label = stringResource(tab.labelRes),
                onClick = { onTabSelected(tab) },
            )
        }
    }
}

@Composable
private fun DownloadsTabPill(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        color = if (selected) tokens.colors.overlaySelected else tokens.colors.surface,
        shape = tokens.shapes.chip,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = NuvioTokens.Space.s12,
                vertical = NuvioTokens.Space.s8,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tokens.colors.textPrimary else tokens.colors.textMuted,
        )
    }
}

private fun LazyListScope.localMediaTabContent(
    state: LocalMediaUiState,
    selectedFolderUri: String?,
    onFolderSelected: (String?) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onPlay: (LocalMediaFile) -> Unit,
    showEmptyState: Boolean,
    showFolderChips: Boolean,
) {
    if (!LocalMediaPlatform.isSupported) {
        if (showEmptyState) {
            item(key = "local_unsupported") {
                DownloadsEmptyState(
                    title = stringResource(Res.string.downloads_local_media_unsupported),
                    message = stringResource(Res.string.downloads_local_media_unsupported_message),
                )
            }
        }
        return
    }

    if (showFolderChips) {
        item(key = "local_folder_chips") {
            LocalMediaFolderChips(
                folders = state.folders,
                selectedFolderUri = selectedFolderUri,
                onFolderSelected = onFolderSelected,
                onRemoveFolder = onRemoveFolder,
            )
        }
    }

    val visibleSeries = if (selectedFolderUri == null) {
        state.series
    } else {
        state.series.mapNotNull { series ->
            val files = series.files.filter { it.folderUri == selectedFolderUri }
            if (files.isEmpty()) null else series.copy(files = files)
        }
    }

    if (visibleSeries.isEmpty()) {
        if (showEmptyState) {
            item(key = "local_empty") {
                DownloadsEmptyState(
                    title = stringResource(Res.string.downloads_local_media_empty_title),
                    message = stringResource(Res.string.downloads_local_media_empty_message),
                )
            }
        }
        return
    }

    if (state.isScanning) {
        item(key = "local_scanning") {
            Text(
                text = stringResource(Res.string.downloads_local_media_scanning),
                style = MaterialTheme.nuvioTypeScale.bodySm,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
        }
    }

    visibleSeries.forEach { series ->
        item(key = "local_series_${series.name}") {
            SectionHeader(
                title = series.name,
                detail = listOf(
                    pluralStringResource(Res.plurals.downloads_episodes, series.files.size, series.files.size),
                    formatBytes(series.files.sumOf { it.sizeBytes }),
                ).joinToString(DownloadTextSeparator),
            )
        }
        items(items = series.files, key = { it.uri }) { file ->
            LocalMediaFileRow(
                file = file,
                onPlay = { onPlay(file) },
            )
        }
    }
}

@Composable
private fun LocalMediaFolderChips(
    folders: List<LocalMediaFolder>,
    selectedFolderUri: String?,
    onFolderSelected: (String?) -> Unit,
    onRemoveFolder: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = NuvioTokens.Space.s4),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        LocalMediaFolderChip(
            selected = selectedFolderUri == null,
            label = stringResource(Res.string.downloads_all_folders),
            onClick = { onFolderSelected(null) },
            onRemove = null,
        )
        folders.forEach { folder ->
            LocalMediaFolderChip(
                selected = selectedFolderUri == folder.uri,
                label = folder.displayName ?: folder.uri,
                onClick = { onFolderSelected(folder.uri) },
                onRemove = { onRemoveFolder(folder.uri) },
            )
        }
    }
}

@Composable
private fun LocalMediaFolderChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        color = if (selected) tokens.colors.overlaySelected else tokens.colors.surface,
        shape = tokens.shapes.chip,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                start = NuvioTokens.Space.s12,
                end = if (onRemove == null) NuvioTokens.Space.s12 else NuvioTokens.Space.s8,
                top = NuvioTokens.Space.s8,
                bottom = NuvioTokens.Space.s8,
            ),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) tokens.colors.textPrimary else tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onRemove != null) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.downloads_remove_folder),
                    modifier = Modifier
                        .size(NuvioTokens.Space.s16)
                        .clickable(onClick = onRemove),
                    tint = tokens.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun LocalMediaFileRow(
    file: LocalMediaFile,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NuvioTokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(Res.string.action_play),
                tint = MaterialTheme.nuvio.colors.textPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(file.sizeBytes),
                style = MaterialTheme.nuvioTypeScale.bodySm,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
        }
    }
}
