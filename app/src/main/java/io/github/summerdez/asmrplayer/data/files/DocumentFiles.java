package io.github.summerdez.asmrplayer.data.files;

import io.github.summerdez.asmrplayer.R;
import io.github.summerdez.asmrplayer.data.*;
import io.github.summerdez.asmrplayer.data.remote.*;
import io.github.summerdez.asmrplayer.data.download.*;
import io.github.summerdez.asmrplayer.data.files.*;
import io.github.summerdez.asmrplayer.domain.*;
import io.github.summerdez.asmrplayer.domain.model.*;
import io.github.summerdez.asmrplayer.playback.*;
import io.github.summerdez.asmrplayer.presentation.*;
import io.github.summerdez.asmrplayer.ui.*;
import io.github.summerdez.asmrplayer.ui.activity.*;
import io.github.summerdez.asmrplayer.ui.components.*;
import io.github.summerdez.asmrplayer.ui.screens.*;
import io.github.summerdez.asmrplayer.ui.theme.*;
import io.github.summerdez.asmrplayer.ui.util.*;
import io.github.summerdez.asmrplayer.di.*;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DocumentFiles {
    private static final String[] FOLDER_COLUMNS = new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
    };

    private DocumentFiles() {
    }

    public static Intent audioPickerIntent(boolean allowMultiple) {
        Intent intent = baseOpenDocumentIntent("audio/*");
        if (allowMultiple) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return intent;
    }

    public static Intent folderPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return intent;
    }

    public static Intent subtitlePickerIntent() {
        Intent intent = baseOpenDocumentIntent("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/x-subrip",
                "text/plain",
                "text/*",
                "application/octet-stream"
        });
        return intent;
    }

    public static Intent imagePickerIntent() {
        return baseOpenDocumentIntent("image/*");
    }

    public static List<Uri> urisFromResult(Intent data) {
        List<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        return uris;
    }

    public static void persistReadPermission(Context context, Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
    }

    public static void persistTreeReadPermission(Context context, Intent data, Uri uri) {
        int flags = data == null ? Intent.FLAG_GRANT_READ_URI_PERMISSION : data.getFlags();
        int readFlags = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (readFlags == 0) {
            readFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }
        try {
            context.getContentResolver().takePersistableUriPermission(uri, readFlags);
        } catch (SecurityException ignored) {
        }
    }

    public static List<FolderImportItem> folderAudioImports(Context context, Uri treeUri) {
        if (treeUri == null) {
            return Collections.emptyList();
        }
        List<TreeDocument> documents = treeDocuments(context, treeUri);
        Map<String, TreeDocument> subtitlesByName = new HashMap<>();
        for (TreeDocument document : documents) {
            if (isSupportedSubtitleName(document.name)) {
                subtitlesByName.put(normalizedName(document.name), document);
            }
        }

        List<FolderImportItem> imports = new ArrayList<>();
        for (TreeDocument document : documents) {
            if (!isSupportedAudioName(document.name, document.mimeType)) {
                continue;
            }
            TreeDocument subtitle = firstMatchingSubtitle(subtitlesByName, document.name);
            imports.add(new FolderImportItem(
                    document.name,
                    document.uri,
                    subtitle == null ? "" : subtitle.name,
                    subtitle == null ? null : subtitle.uri));
        }
        Collections.sort(imports, (left, right) -> left.audioName.compareToIgnoreCase(right.audioName));
        return imports;
    }

    public static String displayName(Context context, Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String name = cursor.getString(index);
                        if (!TextUtils.isEmpty(name)) {
                            return name;
                        }
                    }
                }
            }
        }
        String lastSegment = uri.getLastPathSegment();
        return TextUtils.isEmpty(lastSegment) ? uri.toString() : lastSegment;
    }

    public static long audioDurationMs(Context context, Uri uri) {
        if (context == null || uri == null) {
            return 0L;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (TextUtils.isEmpty(duration)) {
                return 0L;
            }
            return Math.max(0L, Long.parseLong(duration));
        } catch (RuntimeException ignored) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }

    public static String subtitleNameForAudioName(String audioName) {
        return safeName(audioName) + ".vtt";
    }

    public static boolean isSupportedAudioName(String name, String mimeType) {
        String normalized = normalizedName(name);
        if (normalized.endsWith(".vtt")) {
            return false;
        }
        if (mimeType != null && mimeType.startsWith("audio/")) {
            return true;
        }
        return normalized.endsWith(".mp3")
                || normalized.endsWith(".wav")
                || normalized.endsWith(".flac")
                || normalized.endsWith(".m4a")
                || normalized.endsWith(".aac")
                || normalized.endsWith(".ogg")
                || normalized.endsWith(".opus")
                || normalized.endsWith(".amr")
                || normalized.endsWith(".m4b")
                || normalized.endsWith(".3gp");
    }

    private static Intent baseOpenDocumentIntent(String type) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    private static List<TreeDocument> treeDocuments(Context context, Uri treeUri) {
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId);
        List<TreeDocument> documents = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                childrenUri,
                FOLDER_COLUMNS,
                null,
                null,
                null)) {
            if (cursor == null) {
                return documents;
            }
            int idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String documentId = idIndex >= 0 ? cursor.getString(idIndex) : "";
                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : "";
                String mimeType = mimeIndex >= 0 ? cursor.getString(mimeIndex) : "";
                if (TextUtils.isEmpty(documentId)
                        || TextUtils.isEmpty(name)
                        || DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    continue;
                }
                documents.add(new TreeDocument(
                        name,
                        mimeType,
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)));
            }
        } catch (IllegalArgumentException | SecurityException ignored) {
        }
        return documents;
    }

    private static TreeDocument firstMatchingSubtitle(Map<String, TreeDocument> subtitlesByName, String audioName) {
        return subtitlesByName.get(normalizedName(subtitleNameForAudioName(audioName)));
    }

    private static boolean isSupportedSubtitleName(String name) {
        String normalized = normalizedName(name);
        return normalized.endsWith(".vtt");
    }

    private static String normalizedName(String name) {
        return safeName(name).toLowerCase(Locale.ROOT);
    }

    private static String safeName(String name) {
        return name == null ? "" : name.trim();
    }

    public static final class FolderImportItem {
        public final String audioName;
        public final Uri audioUri;
        public final String subtitleName;
        public final Uri subtitleUri;

        public FolderImportItem(String audioName, Uri audioUri, String subtitleName, Uri subtitleUri) {
            this.audioName = audioName == null ? "" : audioName;
            this.audioUri = audioUri;
            this.subtitleName = subtitleName == null ? "" : subtitleName;
            this.subtitleUri = subtitleUri;
        }

        public boolean hasSubtitle() {
            return subtitleUri != null;
        }
    }

    private static final class TreeDocument {
        final String name;
        final String mimeType;
        final Uri uri;

        TreeDocument(String name, String mimeType, Uri uri) {
            this.name = name == null ? "" : name;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.uri = uri;
        }
    }
}
