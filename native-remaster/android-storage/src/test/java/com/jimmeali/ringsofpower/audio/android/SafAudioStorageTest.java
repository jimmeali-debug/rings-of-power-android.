package com.jimmeali.ringsofpower.audio.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.jimmeali.ringsofpower.audio.AudioAssetKind;
import com.jimmeali.ringsofpower.audio.AudioOverrideManager;
import com.jimmeali.ringsofpower.audio.AudioResolution;
import com.jimmeali.ringsofpower.audio.OpenedAudioOverride;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SafAudioStorageTest {
    private static final String ROM_SHA =
            "36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5";

    public static void main(String[] args) throws Exception {
        byte[] music = "music-data".getBytes("UTF-8");
        FakeResolver resolver = new FakeResolver();
        resolver.addDirectory("root", "music", "music-dir");
        resolver.addFile("root", "audio-overrides.json", "manifest", manifest(music));
        resolver.addFile("music-dir", "selector-08.ogg", "music-08", music);

        AudioOverrideManager manager = new AudioOverrideManager();
        SafAudioOverrideController controller = new SafAudioOverrideController(resolver, manager);
        Uri tree = Uri.parse("tree:root");
        controller.activate(tree, ROM_SHA);

        check(controller.activeTreeUri().orElseThrow().equals(tree), "active tree");
        check(resolver.persisted.contains(tree), "persisted permission");
        check(
                manager.resolve(AudioAssetKind.MUSIC, 8).mode() == AudioResolution.Mode.OVERRIDE,
                "override resolution");
        check(
                manager.resolve(AudioAssetKind.MUSIC, 9).mode() == AudioResolution.Mode.ORIGINAL,
                "partial fallback");
        try (OpenedAudioOverride opened =
                manager.openOverride(AudioAssetKind.MUSIC, 8).orElseThrow()) {
            check(Arrays.equals(opened.input().readAllBytes(), music), "provider playback stream");
        }

        SafAudioAssetSource source = new SafAudioAssetSource(resolver, tree);
        expectIllegalArgument(() -> source.resolve("../escape.ogg"));
        expectIllegalArgument(() -> source.resolve("music\\escape.ogg"));

        resolver.files.put("music-08", "tampered".getBytes("UTF-8"));
        expectFailure(() -> controller.activate(tree, ROM_SHA));
        check(resolver.persisted.contains(tree), "same-tree failure retains permission");
        check(manager.activeManifest().isPresent(), "same-tree failure retains active pack");

        Uri broken = Uri.parse("tree:broken");
        resolver.addFile("broken", "audio-overrides.json", "broken-manifest", manifest(music));
        expectFailure(() -> controller.activate(broken, ROM_SHA));
        check(!resolver.persisted.contains(broken), "failed new tree releases permission");
        check(controller.activeTreeUri().orElseThrow().equals(tree), "failed swap is atomic");

        controller.deactivate();
        check(!resolver.persisted.contains(tree), "deactivate releases permission");
        check(!manager.activeManifest().isPresent(), "deactivate clears pack");
        System.out.println("SafAudioStorageTest: all checks passed");
    }

    private static byte[] manifest(byte[] music) throws Exception {
        String json = "{\n"
                + "  \"format\": \"rings-of-power-audio-overrides\",\n"
                + "  \"version\": 1,\n"
                + "  \"rom_sha256\": \"" + ROM_SHA + "\",\n"
                + "  \"partial_pack\": true,\n"
                + "  \"entries\": [{\n"
                + "    \"kind\": \"music\",\n"
                + "    \"id\": 8,\n"
                + "    \"path\": \"music/selector-08.ogg\",\n"
                + "    \"output_sha256\": \"" + sha(music) + "\",\n"
                + "    \"source_duration_seconds\": 1.166667,\n"
                + "    \"target_duration_seconds\": 1.166667,\n"
                + "    \"duration_delta_seconds\": 0.0,\n"
                + "    \"duration_compatible\": true,\n"
                + "    \"sample_rate\": 48000,\n"
                + "    \"channels\": 2,\n"
                + "    \"codec\": \"vorbis\",\n"
                + "    \"bytes\": " + music.length + "\n"
                + "  }]\n"
                + "}";
        return json.getBytes("UTF-8");
    }

    private static String sha(byte[] value) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte item : MessageDigest.getInstance("SHA-256").digest(value)) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed check: " + name);
        }
    }

    private static void expectFailure(ThrowingRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected activation failure");
        } catch (FileNotFoundException expected) {
            // expected
        } catch (java.io.IOException expected) {
            // expected
        }
    }

    private static void expectIllegalArgument(ThrowingRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class FakeResolver extends ContentResolver {
        private final Map<String, List<Row>> children = new HashMap<>();
        private final Map<String, byte[]> files = new HashMap<>();
        private final Set<Uri> persisted = new HashSet<>();

        void addDirectory(String parent, String name, String id) {
            children.computeIfAbsent(parent, unused -> new ArrayList<>())
                    .add(new Row(id, name, DocumentsContract.Document.MIME_TYPE_DIR));
        }

        void addFile(String parent, String name, String id, byte[] contents) {
            children.computeIfAbsent(parent, unused -> new ArrayList<>())
                    .add(new Row(id, name, "application/octet-stream"));
            files.put(id, contents);
        }

        @Override
        public Cursor query(
                Uri uri,
                String[] projection,
                String selection,
                String[] selectionArgs,
                String sortOrder) {
            String value = uri.toString();
            if (!value.startsWith("children:")) {
                return null;
            }
            return new FakeCursor(children.getOrDefault(
                    value.substring("children:".length()), new ArrayList<>()));
        }

        @Override
        public InputStream openInputStream(Uri uri) throws FileNotFoundException {
            String value = uri.toString();
            byte[] contents = value.startsWith("document:")
                    ? files.get(value.substring("document:".length()))
                    : null;
            if (contents == null) {
                throw new FileNotFoundException(value);
            }
            return new ByteArrayInputStream(contents);
        }

        @Override
        public void takePersistableUriPermission(Uri uri, int modeFlags) {
            persisted.add(uri);
        }

        @Override
        public void releasePersistableUriPermission(Uri uri, int modeFlags) {
            persisted.remove(uri);
        }
    }

    private static final class Row {
        private final String id;
        private final String name;
        private final String mime;

        private Row(String id, String name, String mime) {
            this.id = id;
            this.name = name;
            this.mime = mime;
        }
    }

    private static final class FakeCursor implements Cursor {
        private final List<Row> rows;
        private int index = -1;

        private FakeCursor(List<Row> rows) {
            this.rows = rows;
        }

        @Override
        public boolean moveToNext() {
            index++;
            return index < rows.size();
        }

        @Override
        public int getColumnIndexOrThrow(String columnName) {
            return Arrays.asList(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE).indexOf(columnName);
        }

        @Override
        public String getString(int columnIndex) {
            Row row = rows.get(index);
            if (columnIndex == 0) {
                return row.id;
            }
            if (columnIndex == 1) {
                return row.name;
            }
            if (columnIndex == 2) {
                return row.mime;
            }
            throw new IllegalArgumentException("Unknown column " + columnIndex);
        }

        @Override
        public void close() {
        }
    }
}
