package android.provider;

import android.net.Uri;

public final class DocumentsContract {
    private DocumentsContract() {
    }

    public static String getTreeDocumentId(Uri treeUri) {
        String value = treeUri.toString();
        if (!value.startsWith("tree:")) {
            throw new IllegalArgumentException("Not a tree URI: " + value);
        }
        return value.substring("tree:".length());
    }

    public static Uri buildDocumentUriUsingTree(Uri treeUri, String documentId) {
        return Uri.parse("document:" + documentId);
    }

    public static Uri buildChildDocumentsUriUsingTree(Uri treeUri, String documentId) {
        return Uri.parse("children:" + documentId);
    }

    public static final class Document {
        public static final String COLUMN_DOCUMENT_ID = "document_id";
        public static final String COLUMN_DISPLAY_NAME = "display_name";
        public static final String COLUMN_MIME_TYPE = "mime_type";
        public static final String MIME_TYPE_DIR = "vnd.android.document/directory";

        private Document() {
        }
    }
}
