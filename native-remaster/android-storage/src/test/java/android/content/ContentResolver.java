package android.content;

import android.database.Cursor;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.InputStream;

public abstract class ContentResolver {
    public abstract Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder);

    public abstract InputStream openInputStream(Uri uri) throws FileNotFoundException;

    public abstract void takePersistableUriPermission(Uri uri, int modeFlags);

    public abstract void releasePersistableUriPermission(Uri uri, int modeFlags);
}
