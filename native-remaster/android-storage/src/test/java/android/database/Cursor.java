package android.database;

public interface Cursor extends AutoCloseable {
    boolean moveToNext();

    int getColumnIndexOrThrow(String columnName);

    String getString(int columnIndex);

    @Override
    void close();
}
