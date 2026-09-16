package com.jimmeali.ringsofpower.demo;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jimmeali.ringsofpower.audio.AudioAssetKind;
import com.jimmeali.ringsofpower.audio.AudioAssetSource;
import com.jimmeali.ringsofpower.audio.AudioOverrideManager;
import com.jimmeali.ringsofpower.audio.AudioOverrideManifest;
import com.jimmeali.ringsofpower.audio.OpenedAudioOverride;
import com.jimmeali.ringsofpower.audio.android.SafAudioOverrideController;
import com.jimmeali.ringsofpower.rom.RomSceneDecoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DemoActivity extends Activity {
    private static final int PICK_PACK = 41;
    private static final int PICK_ROM = 42;
    private static final String ROM_SHA =
            "36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5";
    private static final String PREFERENCES = "audio-demo";
    private static final String SAVED_TREE = "saved-tree";
    private static final String SAVED_ROM = "saved-rom";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AudioOverrideManager manager = new AudioOverrideManager();
    private SafAudioOverrideController controller;
    private MediaPlayerAudioBackend backend;
    private SharedPreferences preferences;
    private TextView status;
    private DemoGameView game;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        controller = new SafAudioOverrideController(getContentResolver(), manager);
        backend = new MediaPlayerAudioBackend(new File(getCacheDir(), "audio-demo"));
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        setContentView(createContent());
        restoreOrLoadBundledPack();
        restoreRom();
    }

    private View createContent() {
        int padding = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setBackgroundColor(Color.rgb(13, 18, 28));

        TextView title = text("Rings of Power\nNative Remaster Demo", 28, Color.rgb(244, 197, 66));
        title.setGravity(Gravity.CENTER);
        content.addView(title, matchWrap());

        TextView description = text(
                "Explore the clean-room playable scene with touch or a controller. "
                        + "Reach the Sage and press Action to exercise the real verified "
                        + "audio pipeline. You can also import an override-pack folder.",
                16,
                Color.rgb(224, 231, 255));
        description.setPadding(0, dp(18), 0, dp(18));
        content.addView(description, matchWrap());

        game = new DemoGameView(this);
        game.setListener(new DemoGameView.Listener() {
            @Override
            public void onMessage(String message) {
                setStatus(message);
            }

            @Override
            public void onSageInteraction() {
                setStatus("The Sage invokes selector 08…");
                playSelector();
            }
        });
        content.addView(game, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(430)));

        content.addView(button("Select verified Rings of Power ROM", view -> chooseRom()), matchWrap());
        content.addView(button("Play selector 08", view -> playSelector()), matchWrap());
        content.addView(button("Stop", view -> stopPlayback()), matchWrap());
        content.addView(button("Import override pack folder", view -> choosePack()), matchWrap());
        content.addView(button("Use bundled clean-room pack", view -> loadBundledPack()), matchWrap());
        content.addView(button("Deactivate overrides", view -> deactivate()), matchWrap());

        status = text("Starting…", 15, Color.rgb(147, 197, 253));
        status.setPadding(0, dp(20), 0, 0);
        content.addView(status, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private void restoreOrLoadBundledPack() {
        String saved = preferences.getString(SAVED_TREE, null);
        if (saved == null) {
            loadBundledPack();
            return;
        }
        setStatus("Restoring selected pack…");
        worker.execute(() -> {
            try {
                AudioOverrideManifest manifest = controller.activate(Uri.parse(saved), ROM_SHA);
                setStatus("Restored imported pack: " + manifest.entries().size() + " verified entries");
            } catch (Exception failure) {
                preferences.edit().remove(SAVED_TREE).apply();
                activateBundledOnWorker("Saved pack unavailable; bundled demo ready");
            }
        });
    }

    private void loadBundledPack() {
        setStatus("Loading bundled clean-room pack…");
        worker.execute(() -> {
            preferences.edit().remove(SAVED_TREE).apply();
            controller.deactivate();
            activateBundledOnWorker("Bundled clean-room pack ready");
        });
    }

    private void activateBundledOnWorker(String message) {
        try {
            AudioAssetSource source = new AssetAudioAssetSource(getAssets());
            AudioOverrideManifest manifest = AudioOverrideManifest.parse(
                    readUtf8(source.open("audio-overrides.json")));
            manager.activate(manifest, ROM_SHA, source);
            setStatus(message + ": selector 08");
        } catch (Exception failure) {
            setStatus("Bundled pack failed: " + safeMessage(failure));
        }
    }

    private void choosePack() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_PACK);
    }

    private void chooseRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/octet-stream");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_ROM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri tree = data.getData();
        if (requestCode == PICK_ROM) {
            try {
                getContentResolver().takePersistableUriPermission(
                        tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some providers grant access for the current process only.
            }
            preferences.edit().putString(SAVED_ROM, tree.toString()).apply();
            loadRom(tree, "Selected ROM active");
            return;
        }
        if (requestCode != PICK_PACK) {
            return;
        }
        setStatus("Verifying selected pack…");
        worker.execute(() -> {
            try {
                AudioOverrideManifest manifest = controller.activate(tree, ROM_SHA);
                preferences.edit().putString(SAVED_TREE, tree.toString()).apply();
                setStatus("Imported pack active: " + manifest.entries().size() + " verified entries");
            } catch (Exception failure) {
                setStatus("Import rejected: " + safeMessage(failure));
            }
        });
    }

    private void restoreRom() {
        String saved = preferences.getString(SAVED_ROM, null);
        if (saved != null) {
            loadRom(Uri.parse(saved), "Restored ROM scene");
        }
    }

    private void loadRom(Uri uri, String successMessage) {
        setStatus("Hashing and decoding selected ROM…");
        worker.execute(() -> {
            try {
                byte[] rom = readRom(uri);
                RomSceneDecoder.DecodedScene scene = new RomSceneDecoder().decodeVerified(
                        rom, RomSceneDecoder.Scene.SCREEN_B);
                Bitmap bitmap = Bitmap.createBitmap(
                        scene.argb(), scene.width(), scene.height(), Bitmap.Config.ARGB_8888);
                runOnUiThread(() -> {
                    game.setSceneBitmap(bitmap);
                    status.setText(successMessage + " — real screen-b plane decoded in memory");
                });
            } catch (Exception failure) {
                preferences.edit().remove(SAVED_ROM).apply();
                setStatus("ROM rejected; clean-room scene retained: " + safeMessage(failure));
            }
        });
    }

    private byte[] readRom(Uri uri) throws IOException {
        InputStream opened = getContentResolver().openInputStream(uri);
        if (opened == null) {
            throw new IOException("Document provider returned no ROM stream");
        }
        try (InputStream input = opened; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                if (output.size() + count > RomSceneDecoder.SUPPORTED_SIZE) {
                    throw new IOException("ROM is larger than the supported 1 MiB image");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void playSelector() {
        setStatus("Opening verified selector 08…");
        worker.execute(() -> {
            try {
                Optional<OpenedAudioOverride> candidate =
                        manager.openOverride(AudioAssetKind.MUSIC, 8);
                if (!candidate.isPresent()) {
                    setStatus("No selector 08 override; native engine would use original audio");
                    return;
                }
                File staged;
                try (OpenedAudioOverride opened = candidate.get()) {
                    staged = backend.stage(opened);
                }
                backend.play(staged, new MediaPlayerAudioBackend.Listener() {
                    @Override
                    public void onFinished() {
                        setStatus("Playback complete — verified override path works");
                    }

                    @Override
                    public void onError(String message) {
                        setStatus(message);
                    }
                });
                setStatus("Playing verified selector 08");
            } catch (Exception failure) {
                setStatus("Playback failed: " + safeMessage(failure));
            }
        });
    }

    private void stopPlayback() {
        worker.execute(() -> {
            backend.stop();
            setStatus("Playback stopped");
        });
    }

    private void deactivate() {
        worker.execute(() -> {
            backend.stop();
            controller.deactivate();
            preferences.edit().remove(SAVED_TREE).apply();
            setStatus("Overrides inactive; original-audio fallback selected");
        });
    }

    @Override
    protected void onDestroy() {
        backend.stop();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void setStatus(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String readUtf8(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) >= 0) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String safeMessage(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isEmpty()
                ? failure.getClass().getSimpleName()
                : message;
    }
}
