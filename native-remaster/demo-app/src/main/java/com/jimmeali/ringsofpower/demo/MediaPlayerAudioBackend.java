package com.jimmeali.ringsofpower.demo;

import android.media.AudioAttributes;
import android.media.MediaPlayer;

import com.jimmeali.ringsofpower.audio.OpenedAudioOverride;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class MediaPlayerAudioBackend {
    interface Listener {
        void onFinished();

        void onError(String message);
    }

    private final File cacheDirectory;
    private MediaPlayer player;

    MediaPlayerAudioBackend(File cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    File stage(OpenedAudioOverride opened) throws IOException {
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs()) {
            throw new IOException("Cannot create playback cache");
        }
        String name = opened.entry().key().kind().manifestName()
                + "-" + opened.entry().key().id() + ".ogg";
        File temporary = new File(cacheDirectory, name + ".pending");
        File ready = new File(cacheDirectory, name);
        InputStream input = opened.input();
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            output.getFD().sync();
        }
        if (ready.exists() && !ready.delete()) {
            throw new IOException("Cannot replace playback cache");
        }
        if (!temporary.renameTo(ready)) {
            throw new IOException("Cannot activate playback cache");
        }
        return ready;
    }

    synchronized void play(File file, Listener listener) throws IOException {
        stop();
        MediaPlayer next = new MediaPlayer();
        next.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        next.setDataSource(file.getAbsolutePath());
        next.setOnCompletionListener(completed -> {
            synchronized (MediaPlayerAudioBackend.this) {
                if (player == completed) {
                    player.release();
                    player = null;
                }
            }
            listener.onFinished();
        });
        next.setOnErrorListener((failed, what, extra) -> {
            synchronized (MediaPlayerAudioBackend.this) {
                if (player == failed) {
                    player.release();
                    player = null;
                }
            }
            listener.onError("MediaPlayer error " + what + "/" + extra);
            return true;
        });
        try {
            next.prepare();
            player = next;
            next.start();
        } catch (IOException | RuntimeException failure) {
            next.release();
            throw failure;
        }
    }

    synchronized void stop() {
        if (player == null) {
            return;
        }
        player.setOnCompletionListener(null);
        player.setOnErrorListener(null);
        try {
            player.stop();
        } catch (IllegalStateException ignored) {
            // Releasing is still safe after an asynchronous player failure.
        }
        player.release();
        player = null;
    }
}
