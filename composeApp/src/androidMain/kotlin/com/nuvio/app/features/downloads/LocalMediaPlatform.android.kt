package com.nuvio.app.features.downloads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android Local Media: scans SAF tree folders for video files and opens the
 * system folder / file pickers. Mirrors the proven
 * [DownloadsExternalFolderPlatform] picker flow (persistable URI permission,
 * startActivityForResult + handleActivityResult) but keeps its own request
 * codes, so adding a media folder never touches the download-location folder.
 */
internal actual object LocalMediaPlatform {
    private const val requestPickFolder = 51044
    private const val requestPickVideo = 51045
    private const val maxScanDepth = 4
    private const val maxScannedFiles = 3000

    private val videoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts", "mts",
        "flv", "wmv", "3gp", "mpg", "mpeg", "ogv",
    )

    actual val isSupported: Boolean = true

    private var appContext: Context? = null
    private var activityRef: WeakReference<AppCompatActivity>? = null
    private var pendingFolderCallback: ((Result<LocalMediaFolderPick?>) -> Unit)? = null
    private var pendingVideoCallback: ((Result<LocalMediaFile?>) -> Unit)? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun bindActivity(activity: AppCompatActivity) {
        activityRef = WeakReference(activity)
    }

    fun unbindActivity(activity: AppCompatActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
    }

    @Suppress("DEPRECATION")
    actual fun pickFolder(onResult: (Result<LocalMediaFolderPick?>) -> Unit) {
        val activity = activityRef?.get()
        if (activity == null) {
            onResult(Result.failure(IllegalStateException("Folder selection is not available right now.")))
            return
        }

        pendingFolderCallback = onResult
        runCatching {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }
            activity.startActivityForResult(intent, requestPickFolder)
        }.onFailure { error ->
            pendingFolderCallback = null
            onResult(Result.failure(error))
        }
    }

    @Suppress("DEPRECATION")
    actual fun pickVideoFile(onResult: (Result<LocalMediaFile?>) -> Unit) {
        val activity = activityRef?.get()
        if (activity == null) {
            onResult(Result.failure(IllegalStateException("File selection is not available right now.")))
            return
        }

        pendingVideoCallback = onResult
        runCatching {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            activity.startActivityForResult(intent, requestPickVideo)
        }.onFailure { error ->
            pendingVideoCallback = null
            onResult(Result.failure(error))
        }
    }

    actual suspend fun scanFolder(folderUri: String): List<LocalMediaFile> = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext emptyList()
        val treeUri = runCatching { Uri.parse(folderUri) }.getOrNull() ?: return@withContext emptyList()
        val rootName = folderDisplayName(context, treeUri) ?: "Folder"
        val found = ArrayList<LocalMediaFile>()
        scanTree(context, treeUri, treeUri, rootName, rootName, 0, found)
        found
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != requestPickFolder && requestCode != requestPickVideo) return false

        if (requestCode == requestPickFolder) {
            val callback = pendingFolderCallback
            pendingFolderCallback = null
            if (resultCode != Activity.RESULT_OK || data?.data == null) {
                callback?.invoke(Result.success(null))
                return true
            }

            val context = appContext
            val uri = data.data
            if (context == null || uri == null) {
                callback?.invoke(Result.failure(IllegalStateException("Folder selection is not available right now.")))
                return true
            }

            val result = runCatching {
                val takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (takeFlags != 0) {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                }
                LocalMediaFolderPick(
                    uri = uri.toString(),
                    displayName = folderDisplayName(context, uri),
                )
            }
            callback?.invoke(result)
            return true
        }

        val callback = pendingVideoCallback
        pendingVideoCallback = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            callback?.invoke(Result.success(null))
            return true
        }

        val context = appContext
        val uri = data.data
        if (context == null || uri == null) {
            callback?.invoke(Result.failure(IllegalStateException("File selection is not available right now.")))
            return true
        }

        val result = runCatching {
            val takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (takeFlags != 0) {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
            val (displayName, sizeBytes) = videoFileDetails(context, uri)
            LocalMediaFile(
                uri = uri.toString(),
                name = displayName?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: uri.toString(),
                sizeBytes = sizeBytes,
                folderUri = "",
                seriesName = "",
            )
        }
        callback?.invoke(result)
        return true
    }

    private fun scanTree(
        context: Context,
        treeUri: Uri,
        parentUri: Uri,
        parentName: String,
        rootName: String,
        depth: Int,
        out: MutableList<LocalMediaFile>,
    ) {
        if (depth > maxScanDepth || out.size >= maxScannedFiles) return
        val parentDocumentId = runCatching { DocumentsContract.getDocumentId(parentUri) }.getOrNull() ?: return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val rows = runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val rows = ArrayList<DocumentRow>()
                while (cursor.moveToNext()) {
                    if (idIndex < 0 || nameIndex < 0) continue
                    val id = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: continue
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                    val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    rows.add(DocumentRow(id, name, mime, size))
                }
                rows
            }
        }.getOrNull() ?: return

        for (row in rows) {
            if (out.size >= maxScannedFiles) return
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, row.id)
            if (row.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                scanTree(context, treeUri, documentUri, row.name, rootName, depth + 1, out)
            } else if (isVideoFile(row.name, row.mime)) {
                val seriesName = if (depth == 0) rootName else parentName
                out.add(
                    LocalMediaFile(
                        uri = documentUri.toString(),
                        name = row.name,
                        sizeBytes = row.size,
                        folderUri = treeUri.toString(),
                        seriesName = seriesName,
                    ),
                )
            }
        }
    }

    private data class DocumentRow(
        val id: String,
        val name: String,
        val mime: String?,
        val size: Long,
    )

    private fun isVideoFile(name: String, mime: String?): Boolean {
        if (mime != null && mime.startsWith("video/")) return true
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension.isNotBlank() && extension in videoExtensions
    }

    private fun videoFileDetails(context: Context, uri: Uri): Pair<String?, Long> {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null to 0L
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                name to size
            }
        }.getOrNull() ?: (null to 0L)
    }

    private fun folderDisplayName(context: Context, uri: Uri): String? {
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocumentId)

        return runCatching {
            context.contentResolver.query(
                documentUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val displayNameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (displayNameIndex >= 0) {
                    cursor.getString(displayNameIndex)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: treeDocumentId
                .substringAfter(':')
                .substringAfterLast('/')
                .takeIf { it.isNotBlank() }
    }
}
