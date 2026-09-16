package com.jimmeali.ringsofpower.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

final class GameView extends View {
    interface Listener {
        void onProgressChanged(GameProgress progress);
        void onAreaRequested(GameProgress progress);
        void onMessage(String message);
        void onSageInteraction();
    }

    private static final int TILES_X = 12;
    private static final int TILES_Y = 8;
    private static final float SAGE_X = 8.5f;
    private static final float SAGE_Y = 3.5f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF up = new RectF();
    private final RectF down = new RectF();
    private final RectF left = new RectF();
    private final RectF right = new RectF();
    private final RectF action = new RectF();
    private Listener listener;
    private GameProgress progress = GameProgress.newGame();
    private Bitmap sceneBitmap;
    private float directionX;
    private float directionY;
    private long previousFrame;
    private boolean loadingArea;

    GameView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription("Rings of Power game world");
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setProgress(GameProgress progress) {
        this.progress = progress;
        loadingArea = false;
        invalidate();
    }

    void setSceneBitmap(Bitmap bitmap) {
        sceneBitmap = bitmap;
        loadingArea = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (!loadingArea && previousFrame != 0 && (directionX != 0 || directionY != 0)) {
            float seconds = Math.min(0.05f, (now - previousFrame) / 1000.0f);
            move(directionX * seconds * 3.2f, directionY * seconds * 3.2f);
        }
        previousFrame = now;

        float controlsHeight = Math.min(getHeight() * 0.34f, dp(150));
        float worldHeight = getHeight() - controlsHeight;
        float tile = Math.min(getWidth() / (float) TILES_X, worldHeight / TILES_Y);
        float offsetX = (getWidth() - tile * TILES_X) / 2.0f;
        float offsetY = (worldHeight - tile * TILES_Y) / 2.0f;

        canvas.drawColor(Color.rgb(8, 13, 22));
        paint.setStyle(Paint.Style.FILL);
        if (sceneBitmap != null) {
            paint.setFilterBitmap(false);
            canvas.drawBitmap(sceneBitmap, null,
                    new RectF(offsetX, offsetY, offsetX + tile * TILES_X,
                            offsetY + tile * TILES_Y), paint);
        } else {
            paint.setColor(Color.rgb(15, 23, 42));
            canvas.drawRect(offsetX, offsetY, offsetX + tile * TILES_X,
                    offsetY + tile * TILES_Y, paint);
        }

        if (progress.area == GameProgress.AREA_SAGE) {
            float x = offsetX + SAGE_X * tile;
            float y = offsetY + SAGE_Y * tile;
            paint.setColor(Color.rgb(147, 197, 253));
            canvas.drawCircle(x, y, tile * 0.30f, paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(tile * 0.25f);
            canvas.drawText("SAGE", x, y - tile * 0.43f, paint);
        }

        drawPlayer(canvas, offsetX + progress.playerX * tile,
                offsetY + progress.playerY * tile, tile);
        layoutControls(worldHeight, controlsHeight);
        drawControl(canvas, up, "▲", directionY < 0);
        drawControl(canvas, down, "▼", directionY > 0);
        drawControl(canvas, left, "◀", directionX < 0);
        drawControl(canvas, right, "▶", directionX > 0);
        drawControl(canvas, action, "ACTION", false);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(12));
        paint.setColor(Color.rgb(203, 213, 225));
        String objective = progress.sageMet
                ? "Quest started — explore the two restored areas"
                : progress.area == GameProgress.AREA_SAGE
                        ? "Find the Sage and press Action"
                        : "Travel east to find the Sage";
        canvas.drawText(objective, getWidth() / 2.0f, worldHeight + dp(18), paint);
        if (loadingArea) {
            paint.setColor(0xaa000000);
            canvas.drawRect(offsetX, offsetY, offsetX + tile * TILES_X,
                    offsetY + tile * TILES_Y, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(18));
            canvas.drawText("Loading area…", getWidth() / 2.0f, worldHeight / 2.0f, paint);
        }
        if (directionX != 0 || directionY != 0) {
            postInvalidateOnAnimation();
        }
    }

    private void drawPlayer(Canvas canvas, float x, float y, float tile) {
        paint.setColor(Color.rgb(244, 197, 66));
        canvas.drawCircle(x, y + tile * 0.05f, tile * 0.24f, paint);
        paint.setColor(Color.rgb(255, 242, 168));
        canvas.drawCircle(x, y - tile * 0.20f, tile * 0.12f, paint);
        paint.setColor(Color.rgb(55, 48, 34));
        canvas.drawRect(x - tile * 0.16f, y + tile * 0.20f,
                x - tile * 0.03f, y + tile * 0.39f, paint);
        canvas.drawRect(x + tile * 0.03f, y + tile * 0.20f,
                x + tile * 0.16f, y + tile * 0.39f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int actionMasked = event.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
            setDirection(0, 0);
            return true;
        }
        if (actionMasked != MotionEvent.ACTION_DOWN && actionMasked != MotionEvent.ACTION_MOVE) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        if (action.contains(x, y)) {
            setDirection(0, 0);
            if (actionMasked == MotionEvent.ACTION_DOWN) interact();
        } else if (up.contains(x, y)) setDirection(0, -1);
        else if (down.contains(x, y)) setDirection(0, 1);
        else if (left.contains(x, y)) setDirection(-1, 0);
        else if (right.contains(x, y)) setDirection(1, 0);
        else setDirection(0, 0);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) setDirection(0, -1);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) setDirection(0, 1);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) setDirection(-1, 0);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) setDirection(1, 0);
        else if (keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_ENTER) interact();
        else return super.onKeyDown(keyCode, event);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            setDirection(0, 0);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void move(float deltaX, float deltaY) {
        float nextX = progress.playerX + deltaX;
        float nextY = clamp(progress.playerY + deltaY, 0.5f, TILES_Y - 0.5f);
        if (nextX > TILES_X - 0.38f && progress.area == GameProgress.AREA_BEGINNING) {
            requestArea(progress.entered(GameProgress.AREA_SAGE, 0.65f, nextY));
            return;
        }
        if (nextX < 0.38f && progress.area == GameProgress.AREA_SAGE) {
            requestArea(progress.entered(GameProgress.AREA_BEGINNING, 11.35f, nextY));
            return;
        }
        nextX = clamp(nextX, 0.5f, TILES_X - 0.5f);
        if (progress.area == GameProgress.AREA_SAGE
                && Math.hypot(nextX - SAGE_X, nextY - SAGE_Y) < 0.55f) {
            return;
        }
        progress = progress.moved(nextX, nextY);
        if (listener != null) listener.onProgressChanged(progress);
    }

    private void requestArea(GameProgress next) {
        loadingArea = true;
        setDirection(0, 0);
        if (listener != null) listener.onAreaRequested(next);
    }

    private void interact() {
        if (listener == null || loadingArea) return;
        if (progress.area == GameProgress.AREA_SAGE
                && Math.hypot(progress.playerX - SAGE_X, progress.playerY - SAGE_Y) <= 1.35f) {
            listener.onSageInteraction();
        } else {
            listener.onMessage(progress.area == GameProgress.AREA_BEGINNING
                    ? "The road continues east."
                    : "There is nothing to use here.");
        }
    }

    private void setDirection(float x, float y) {
        directionX = x;
        directionY = y;
        previousFrame = SystemClock.uptimeMillis();
        requestFocus();
        postInvalidateOnAnimation();
    }

    private void layoutControls(float top, float height) {
        float size = Math.min(dp(54), height * 0.34f);
        float centerX = dp(92);
        float centerY = top + height * 0.61f;
        up.set(centerX - size / 2, centerY - size * 1.5f, centerX + size / 2, centerY - size / 2);
        down.set(centerX - size / 2, centerY + size / 2, centerX + size / 2, centerY + size * 1.5f);
        left.set(centerX - size * 1.5f, centerY - size / 2, centerX - size / 2, centerY + size / 2);
        right.set(centerX + size / 2, centerY - size / 2, centerX + size * 1.5f, centerY + size / 2);
        float actionSize = size * 1.35f;
        float actionX = getWidth() - dp(82);
        action.set(actionX - actionSize / 2, centerY - actionSize / 2,
                actionX + actionSize / 2, centerY + actionSize / 2);
    }

    private void drawControl(Canvas canvas, RectF bounds, String label, boolean pressed) {
        paint.setColor(pressed ? Color.rgb(244, 197, 66) : Color.rgb(51, 65, 85));
        canvas.drawRoundRect(bounds, dp(12), dp(12), paint);
        paint.setColor(pressed ? Color.rgb(15, 23, 42) : Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(label.length() > 2 ? dp(13) : dp(22));
        canvas.drawText(label, bounds.centerX(),
                bounds.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
