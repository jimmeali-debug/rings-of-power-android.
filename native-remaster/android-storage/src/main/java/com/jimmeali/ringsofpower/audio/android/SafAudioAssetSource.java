package com.jimmeali.ringsofpower.audio.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.jimmeali.ringsofpower.audio.AudioAssetSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class SafAudioAssetSource implements AudioAssetSource {
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String[] PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
    };

    private final ContentResolver resolver;
    private final Uri treeUri;
    private final String rootDocumentId;
    private final Map<String, Uri> cache = new ConcurrentHashMap<>();

    public SafAudioAssetSource(ContentResolver resolver, Uri treeUri) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.treeUri = Objects.requireNonNull(treeUri, "treeUri");
        this.rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        cache.put("", DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId));
    }

    public Uri treeUri() {
        return treeUri;
    }

    @Override
    public InputStream open(String relativePath) throws IOException {
        Uri document = resolve(relativePath);
        InputStream input = resolver.openInputStream(document);
        if (input == null) {
            throw new FileNotFoundException("Document provider returned no stream: " + relativePath);
        }
        return input;
    }

    public Uri resolve(String relativePath) throws IOException {
        validatePath(relativePath);
        Uri cached = cache.get(relativePath);
        if (cached != null) {
            return cached;
        }

        String parentId = rootDocumentId;
        StringBuilder traversed = new StringBuilder();
        String[] segments = relativePath.split("/");
        Uri result = null;
        for (int index = 0; index < segments.length; index++) {
            if (traversed.length() > 0) {
                traversed.append('/');
            }
            traversed.append(segments[index]);
            String cacheKey = traversed.toString();
            Uri segmentUri = cache.get(cacheKey);
            Child child;
            if (segmentUri == null) {
                child = findUniqueChild(parentId, segments[index]);
                segmentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId);
                cache.put(cacheKey, segmentUri);
            } else {
                child = findUniqueChild(parentId, segments[index]);
            }
            boolean finalSegment = index == segments.length - 1;
            if (!finalSegment && !DocumentsContract.Document.MIME_TYPE_DIR.equals(child.mimeType)) {
                throw new FileNotFoundException("Path component is not a directory: " + cacheKey);
            }
            parentId = child.documentId;
            result = segmentUri;
        }
        return result;
    }

    private Child findUniqueChild(String parentId, String displayName) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        Child match = null;
        try (Cursor cursor = resolver.query(children, PROJECTION, null, null, null)) {
            if (cursor == null) {
                throw new IOException("Document provider returned no cursor for " + displayName);
            }
            int idColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                if (!displayName.equals(cursor.getString(nameColumn))) {
                    continue;
                }
                if (match != null) {
                    throw new IOException("Ambiguous duplicate document name: " + displayName);
                }
                match = new Child(cursor.getString(idColumn), cursor.getString(mimeColumn));
            }
        }
        if (match == null) {
            throw new FileNotFoundException("Missing document: " + displayName);
        }
        return match;
    }

    private static void validatePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty() || relativePath.startsWith("/")
                || relativePath.endsWith("/") || relativePath.contains("\\")) {
            throw new IllegalArgumentException("Unsafe document path: " + relativePath);
        }
        for (String segment : relativePath.split("/")) {
            if (segment.equals(".") || segment.equals("..") || !SAFE_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("Unsafe document path: " + relativePath);
            }
        }
    }

    private static final class Child {
        private final String documentId;
        private final String mimeType;

        private Child(String documentId, String mimeType) {
            this.documentId = documentId;
            this.mimeType = mimeType;
        }
    }
}
