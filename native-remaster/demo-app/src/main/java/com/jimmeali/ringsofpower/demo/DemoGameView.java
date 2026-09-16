package com.jimmeali.ringsofpower.demo;

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

final class DemoGameView extends View {
    interface Listener {
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
    private float playerX = 2.5f;
    private float playerY = 6.5f;
    private float directionX;
    private float directionY;
    private long previousFrame;
    private Listener listener;
    private Bitmap sceneBitmap;

    DemoGameView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription("Playable clean-room top-down demo");
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setSceneBitmap(Bitmap sceneBitmap) {
        this.sceneBitmap = sceneBitmap;
        setContentDescription(sceneBitmap == null
                ? "Playable clean-room top-down demo"
                : "Playable scene decoded from the selected Rings of Power ROM");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (previousFrame != 0 && (directionX != 0 || directionY != 0)) {
            float seconds = Math.min(0.05f, (now - previousFrame) / 1000.0f);
            move(directionX * seconds * 3.2f, directionY * seconds * 3.2f);
        }
        previousFrame = now;

        float controlHeight = Math.min(getHeight() * 0.34f, dp(150));
        float worldHeight = getHeight() - controlHeight;
        float tile = Math.min(getWidth() / (float) TILES_X, worldHeight / TILES_Y);
        float offsetX = (getWidth() - tile * TILES_X) / 2.0f;
        float offsetY = (worldHeight - tile * TILES_Y) / 2.0f;

        paint.setStyle(Paint.Style.FILL);
        canvas.drawColor(Color.rgb(8, 13, 22));
        if (sceneBitmap != null) {
            paint.setFilterBitmap(false);
            canvas.drawBitmap(
                    sceneBitmap,
                    null,
                    new RectF(
                            offsetX,
                            offsetY,
                            offsetX + tile * TILES_X,
                            offsetY + tile * TILES_Y),
                    paint);
        } else {
            for (int y = 0; y < TILES_Y; y++) {
                for (int x = 0; x < TILES_X; x++) {
                    paint.setColor(tileColor(x, y));
                    canvas.drawRect(
                            offsetX + x * tile,
                            offsetY + y * tile,
                            offsetX + (x + 1) * tile + 1,
                            offsetY + (y + 1) * tile + 1,
                            paint);
                }
            }
        }

        float sageScreenX = offsetX + SAGE_X * tile;
        float sageScreenY = offsetY + SAGE_Y * tile;
        paint.setColor(Color.rgb(147, 197, 253));
        canvas.drawCircle(sageScreenX, sageScreenY, tile * 0.30f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(tile * 0.28f);
        canvas.drawText("SAGE", sageScreenX, sageScreenY - tile * 0.44f, paint);

        float playerScreenX = offsetX + playerX * tile;
        float playerScreenY = offsetY + playerY * tile;
        paint.setColor(Color.rgb(244, 197, 66));
        canvas.drawCircle(playerScreenX, playerScreenY, tile * 0.28f, paint);
        paint.setColor(Color.rgb(255, 242, 168));
        canvas.drawCircle(playerScreenX, playerScreenY - tile * 0.09f, tile * 0.10f, paint);

        layoutControls(worldHeight, controlHeight);
        drawControl(canvas, up, "▲", directionY < 0);
        drawControl(canvas, down, "▼", directionY > 0);
        drawControl(canvas, left, "◀", directionX < 0);
        drawControl(canvas, right, "▶", directionX > 0);
        drawControl(canvas, action, "ACTION", false);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(13));
        paint.setColor(Color.rgb(203, 213, 225));
        canvas.drawText(
                "Reach the Sage, then press Action",
                getWidth() / 2.0f,
                worldHeight + dp(18),
                paint);

        if (directionX != 0 || directionY != 0) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            setDirection(0, 0);
            return true;
        }
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                && event.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        if (action.contains(x, y)) {
            setDirection(0, 0);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                interact();
            }
        } else if (up.contains(x, y)) {
            setDirection(0, -1);
        } else if (down.contains(x, y)) {
            setDirection(0, 1);
        } else if (left.contains(x, y)) {
            setDirection(-1, 0);
        } else if (right.contains(x, y)) {
            setDirection(1, 0);
        } else {
            setDirection(0, 0);
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            setDirection(0, -1);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            setDirection(0, 1);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            setDirection(-1, 0);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            setDirection(1, 0);
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_ENTER) {
            interact();
        } else {
            return super.onKeyDown(keyCode, event);
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            setDirection(0, 0);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void move(float deltaX, float deltaY) {
        float nextX = clamp(playerX + deltaX, 0.5f, TILES_X - 0.5f);
        float nextY = clamp(playerY + deltaY, 0.5f, TILES_Y - 0.5f);
        if (sceneBitmap != null || !isWater((int) nextX, (int) nextY)) {
            playerX = nextX;
            playerY = nextY;
        }
    }

    private void interact() {
        if (listener == null) {
            return;
        }
        float distance = (float) Math.hypot(playerX - SAGE_X, playerY - SAGE_Y);
        if (distance <= 1.3f) {
            listener.onSageInteraction();
        } else {
            listener.onMessage("The Sage is too far away — use the D-pad to approach");
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
        action.set(
                actionX - actionSize / 2,
                centerY - actionSize / 2,
                actionX + actionSize / 2,
                centerY + actionSize / 2);
    }

    private void drawControl(Canvas canvas, RectF bounds, String label, boolean pressed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? Color.rgb(244, 197, 66) : Color.rgb(51, 65, 85));
        canvas.drawRoundRect(bounds, dp(12), dp(12), paint);
        paint.setColor(pressed ? Color.rgb(15, 23, 42) : Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(label.length() > 2 ? dp(13) : dp(22));
        canvas.drawText(label, bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
    }

    private static int tileColor(int x, int y) {
        if (isWater(x, y)) {
            return Color.rgb(30, 86, 120);
        }
        if ((x + y * 2) % 7 == 0) {
            return Color.rgb(92, 78, 48);
        }
        return (x + y) % 2 == 0 ? Color.rgb(42, 105, 68) : Color.rgb(48, 116, 74);
    }

    private static boolean isWater(int x, int y) {
        if ((x <= 3 && y >= 5) || (x >= 7 && x <= 9 && y >= 2 && y <= 5)) {
            return false;
        }
        return (x * 7 + y * 11) % 17 == 0;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
