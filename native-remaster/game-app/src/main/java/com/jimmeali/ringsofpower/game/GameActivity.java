package com.jimmeali.ringsofpower.game;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jimmeali.ringsofpower.rom.RomSceneDecoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GameActivity extends Activity {
    private static final int PICK_ROM = 71;
    private static final String PREFS = "rings-of-power-game";
    private static final String KEY_ROM = "rom";
    private static final String KEY_HAS_SAVE = "has-save";
    private static final String KEY_AREA = "area";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_SAGE = "sage";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ToneGenerator tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
    private SharedPreferences preferences;
    private LinearLayout menu;
    private LinearLayout gamePanel;
    private TextView status;
    private Button continueButton;
    private GameView gameView;
    private GameProgress progress = GameProgress.newGame();
    private byte[] loadedRom;
    private Uri romUri;
    private boolean startNewAfterPick;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        String savedUri = preferences.getString(KEY_ROM, null);
        if (savedUri != null) romUri = Uri.parse(savedUri);
        setContentView(createContent());
        showTitle();
    }

    private View createContent() {
        int padding = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(8, 13, 22));

        TextView title = text("RINGS OF POWER", 30, Color.rgb(244, 197, 66));
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = text("Native Android Edition", 15, Color.rgb(203, 213, 225));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle, matchWrap());

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView intro = text(
                "Begin the restored adventure using your legally obtained Genesis ROM. "
                        + "Progress and position save automatically.",
                16, Color.WHITE);
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(dp(8), dp(20), dp(8), dp(20));
        menu.addView(intro, matchWrap());
        continueButton = button("Continue", v -> continueGame());
        menu.addView(continueButton, matchWrap());
        menu.addView(button("New Game", v -> newGame()), matchWrap());
        menu.addView(button("Choose Original ROM", v -> chooseRom(false)), matchWrap());
        root.addView(menu, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gameView = new GameView(this);
        gameView.setListener(new GameView.Listener() {
            @Override public void onProgressChanged(GameProgress next) {
                progress = next;
                saveProgress();
            }

            @Override public void onAreaRequested(GameProgress next) {
                loadArea(next, "Entered " + areaName(next.area));
            }

            @Override public void onMessage(String message) {
                setStatus(message);
            }

            @Override public void onSageInteraction() {
                meetSage();
            }
        });
        gamePanel.addView(gameView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout gameButtons = new LinearLayout(this);
        gameButtons.setOrientation(LinearLayout.HORIZONTAL);
        gameButtons.addView(button("Save", v -> {
            saveProgress();
            setStatus("Journey saved");
        }), weighted());
        gameButtons.addView(button("Main Menu", v -> showTitle()), weighted());
        gamePanel.addView(gameButtons, matchWrap());
        root.addView(gamePanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        status = text("", 14, Color.rgb(147, 197, 253));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(8), 0, 0);
        root.addView(status, matchWrap());
        return root;
    }

    private void showTitle() {
        menu.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        continueButton.setEnabled(preferences.getBoolean(KEY_HAS_SAVE, false));
        setStatus(romUri == null ? "Choose your original ROM to begin" : "Original ROM selected");
    }

    private void newGame() {
        if (romUri == null) {
            chooseRom(true);
            return;
        }
        progress = GameProgress.newGame();
        saveProgress();
        loadArea(progress, "A new journey begins");
    }

    private void continueGame() {
        if (romUri == null) {
            chooseRom(false);
            return;
        }
        progress = new GameProgress(
                preferences.getInt(KEY_AREA, GameProgress.AREA_BEGINNING),
                preferences.getFloat(KEY_X, 2.5f),
                preferences.getFloat(KEY_Y, 6.5f),
                preferences.getBoolean(KEY_SAGE, false));
        loadArea(progress, "Journey restored");
    }

    private void chooseRom(boolean beginNewGame) {
        startNewAfterPick = beginNewGame;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_ROM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ROM || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        Uri selected = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    selected, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers grant the selected document for this app without persistence.
        }
        Uri previous = romUri;
        setStatus("Verifying original ROM…");
        worker.execute(() -> {
            try {
                byte[] candidate = readRom(selected);
                new RomSceneDecoder().verifyRom(candidate);
                loadedRom = candidate;
                romUri = selected;
                preferences.edit().putString(KEY_ROM, selected.toString()).apply();
                if (previous != null && !previous.equals(selected)) releasePermission(previous);
                runOnUiThread(() -> {
                    if (startNewAfterPick) newGame();
                    else {
                        setStatus("Original ROM verified");
                        showTitle();
                    }
                });
            } catch (Exception failure) {
                releasePermission(selected);
                setStatus("ROM rejected: " + safeMessage(failure));
            }
        });
    }

    private void loadArea(GameProgress next, String message) {
        menu.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        setStatus("Loading " + areaName(next.area) + "…");
        worker.execute(() -> {
            try {
                byte[] rom = loadedRom;
                if (rom == null) {
                    if (romUri == null) throw new IOException("No original ROM selected");
                    rom = readRom(romUri);
                    new RomSceneDecoder().verifyRom(rom);
                    loadedRom = rom;
                }
                RomSceneDecoder.Scene scene = next.area == GameProgress.AREA_SAGE
                        ? RomSceneDecoder.Scene.SCREEN_B : RomSceneDecoder.Scene.SCREEN_A;
                RomSceneDecoder.DecodedScene decoded = new RomSceneDecoder().decodeVerified(rom, scene);
                Bitmap bitmap = Bitmap.createBitmap(
                        decoded.argb(), decoded.width(), decoded.height(), Bitmap.Config.ARGB_8888);
                runOnUiThread(() -> {
                    progress = next;
                    gameView.setProgress(next);
                    gameView.setSceneBitmap(bitmap);
                    saveProgress();
                    setStatus(message + " — autosaved");
                    gameView.requestFocus();
                });
            } catch (Exception failure) {
                loadedRom = null;
                setStatus("Could not load area: " + safeMessage(failure));
                runOnUiThread(this::showTitle);
            }
        });
    }

    private void meetSage() {
        if (!progress.sageMet) {
            progress = progress.metSage();
            gameView.setProgress(progress);
            saveProgress();
            tones.startTone(ToneGenerator.TONE_PROP_ACK, 220);
            setStatus("Sage: The rings are scattered. Your true journey begins now.");
        } else {
            tones.startTone(ToneGenerator.TONE_PROP_BEEP2, 120);
            setStatus("Sage: Search every road. The world remembers your progress.");
        }
    }

    private void saveProgress() {
        preferences.edit()
                .putBoolean(KEY_HAS_SAVE, true)
                .putInt(KEY_AREA, progress.area)
                .putFloat(KEY_X, progress.playerX)
                .putFloat(KEY_Y, progress.playerY)
                .putBoolean(KEY_SAGE, progress.sageMet)
                .apply();
    }

    private byte[] readRom(Uri uri) throws IOException {
        InputStream opened = getContentResolver().openInputStream(uri);
        if (opened == null) throw new IOException("Document provider returned no stream");
        try (InputStream input = opened; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (output.size() + count > RomSceneDecoder.SUPPORTED_SIZE) {
                    throw new IOException("ROM is larger than the supported 1 MiB image");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void releasePermission(Uri uri) {
        try {
            getContentResolver().releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Permission may already be absent.
        }
    }

    private void setStatus(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        tones.release();
        super.onDestroy();
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
        params.setMargins(0, dp(4), 0, dp(4));
        return params;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private static String areaName(int area) {
        return area == GameProgress.AREA_SAGE ? "the Sage's Crossing" : "the Western Road";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safeMessage(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isEmpty()
                ? failure.getClass().getSimpleName() : message;
    }
}
