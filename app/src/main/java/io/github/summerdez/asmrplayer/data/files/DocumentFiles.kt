package io.github.summerdez.asmrplayer.data.files

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.TextUtils
import java.io.IOException
import java.util.Collections
import java.util.Locale

object DocumentFiles {
    private val FOLDER_COLUMNS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )

    fun audioPickerIntent(allowMultiple: Boolean): Intent {
        val intent = baseOpenDocumentIntent("audio/*")
        if (allowMultiple) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return intent
    }

    fun folderPickerIntent(): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
        return intent
    }

    fun subtitlePickerIntent(): Intent {
        val intent = baseOpenDocumentIntent("*/*")
        intent.putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf(
                "application/x-subrip",
                "text/plain",
                "text/*",
                "application/octet-stream",
            ),
        )
        return intent
    }

    fun imagePickerIntent(): Intent {
        return baseOpenDocumentIntent("image/*")
    }

    fun urisFromResult(data: Intent?): List<Uri> {
        val uris = ArrayList<Uri>()
        val clipData: ClipData? = data!!.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri
                if (uri != null) {
                    uris.add(uri)
                }
            }
        } else if (data.data != null) {
            uris.add(data.data!!)
        }
        return uris
    }

    fun persistReadPermission(context: Context?, uri: Uri?) {
        try {
            context!!.contentResolver.takePersistableUriPermission(
                platformValue(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (ignored: SecurityException) {
        }
    }

    fun persistTreeReadPermission(context: Context?, data: Intent?, uri: Uri?) {
        val flags = data?.flags ?: Intent.FLAG_GRANT_READ_URI_PERMISSION
        var readFlags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readFlags == 0) {
            readFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context!!.contentResolver.takePersistableUriPermission(platformValue(uri), readFlags)
        } catch (ignored: SecurityException) {
        }
    }

    fun folderAudioImports(context: Context?, treeUri: Uri?): List<FolderImportItem> {
        if (treeUri == null) {
            return Collections.emptyList()
        }
        val documents = treeDocuments(context, treeUri)
        val subtitlesByName = HashMap<String, TreeDocument>()
        for (document in documents) {
            if (isSupportedSubtitleName(document.name)) {
                subtitlesByName[normalizedName(document.name)] = document
            }
        }

        val imports = ArrayList<FolderImportItem>()
        for (document in documents) {
            if (!isSupportedAudioName(document.name, document.mimeType)) {
                continue
            }
            val subtitle = firstMatchingSubtitle(subtitlesByName, document.name)
            imports.add(
                FolderImportItem(
                    document.name,
                    document.uri,
                    subtitle?.name ?: "",
                    subtitle?.uri,
                ),
            )
        }
        imports.sortWith { left, right -> String.CASE_INSENSITIVE_ORDER.compare(left.audioName, right.audioName) }
        return imports
    }

    fun displayName(context: Context?, uri: Uri?): String {
        val targetUri = uri!!
        if (ContentResolver.SCHEME_CONTENT == targetUri.scheme) {
            context!!.contentResolver.query(
                targetUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val name = cursor.getString(index)
                        if (!TextUtils.isEmpty(name)) {
                            return name
                        }
                    }
                }
            }
        }
        val lastSegment = targetUri.lastPathSegment
        return if (TextUtils.isEmpty(lastSegment)) targetUri.toString() else lastSegment!!
    }

    fun audioDurationMs(context: Context?, uri: Uri?): Long {
        if (context == null || uri == null) {
            return 0L
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (TextUtils.isEmpty(duration)) {
                return 0L
            }
            return maxOf(0L, java.lang.Long.parseLong(duration!!))
        } catch (ignored: RuntimeException) {
            return 0L
        } finally {
            try {
                retriever.release()
            } catch (ignored: IOException) {
            } catch (ignored: RuntimeException) {
            }
        }
    }

    fun subtitleNameForAudioName(audioName: String?): String {
        return safeName(audioName) + ".vtt"
    }

    fun isSupportedAudioName(name: String?, mimeType: String?): Boolean {
        val normalized = normalizedName(name)
        if (normalized.endsWith(".vtt")) {
            return false
        }
        if (mimeType != null && mimeType.startsWith("audio/")) {
            return true
        }
        return normalized.endsWith(".mp3") ||
            normalized.endsWith(".wav") ||
            normalized.endsWith(".flac") ||
            normalized.endsWith(".m4a") ||
            normalized.endsWith(".aac") ||
            normalized.endsWith(".ogg") ||
            normalized.endsWith(".opus") ||
            normalized.endsWith(".amr") ||
            normalized.endsWith(".m4b") ||
            normalized.endsWith(".3gp")
    }

    private fun baseOpenDocumentIntent(type: String): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = type
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
        return intent
    }

    private fun treeDocuments(context: Context?, treeUri: Uri): List<TreeDocument> {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val documents = ArrayList<TreeDocument>()
        try {
            context!!.contentResolver.query(
                childrenUri,
                FOLDER_COLUMNS,
                null,
                null,
                null,
            )?.use { cursor ->
                collectTreeDocuments(cursor, treeUri, documents)
            } ?: return documents
        } catch (ignored: IllegalArgumentException) {
        } catch (ignored: SecurityException) {
        }
        return documents
    }

    private fun collectTreeDocuments(cursor: Cursor, treeUri: Uri, documents: MutableList<TreeDocument>) {
        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (cursor.moveToNext()) {
            val documentId = if (idIndex >= 0) cursor.getString(idIndex) else ""
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
            val mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex) else ""
            if (
                TextUtils.isEmpty(documentId) ||
                TextUtils.isEmpty(name) ||
                DocumentsContract.Document.MIME_TYPE_DIR == mimeType
            ) {
                continue
            }
            documents.add(
                TreeDocument(
                    name,
                    mimeType,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                ),
            )
        }
    }

    private fun firstMatchingSubtitle(
        subtitlesByName: Map<String, TreeDocument>,
        audioName: String?,
    ): TreeDocument? {
        return subtitlesByName[normalizedName(subtitleNameForAudioName(audioName))]
    }

    private fun isSupportedSubtitleName(name: String?): Boolean {
        val normalized = normalizedName(name)
        return normalized.endsWith(".vtt")
    }

    private fun normalizedName(name: String?): String {
        return safeName(name).lowercase(Locale.ROOT)
    }

    private fun safeName(name: String?): String {
        return name?.trim() ?: ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> platformValue(value: T?): T {
        return value as T
    }

    class FolderImportItem(
        audioName: String?,
        audioUri: Uri?,
        subtitleName: String?,
        val subtitleUri: Uri?,
    ) {
        val audioName: String = audioName ?: ""

        val audioUri: Uri = run {
            @Suppress("UNCHECKED_CAST")
            fun <T> platformFieldValue(value: T?): T {
                return value as T
            }

            platformFieldValue(audioUri)
        }

        val subtitleName: String = subtitleName ?: ""

        fun hasSubtitle(): Boolean {
            return subtitleUri != null
        }
    }

    private class TreeDocument(
        name: String?,
        mimeType: String?,
        val uri: Uri?,
    ) {
        val name: String = name ?: ""
        val mimeType: String = mimeType ?: ""
    }
}
