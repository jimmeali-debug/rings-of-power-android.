package com.jimmeali.ringsofpower.game;

final class GameProgress {
    static final int AREA_BEGINNING = 0;
    static final int AREA_SAGE = 1;

    final int area;
    final float playerX;
    final float playerY;
    final boolean sageMet;

    GameProgress(int area, float playerX, float playerY, boolean sageMet) {
        this.area = area == AREA_SAGE ? AREA_SAGE : AREA_BEGINNING;
        this.playerX = clamp(playerX, 0.5f, 11.5f);
        this.playerY = clamp(playerY, 0.5f, 7.5f);
        this.sageMet = sageMet;
    }

    static GameProgress newGame() {
        return new GameProgress(AREA_BEGINNING, 2.5f, 6.5f, false);
    }

    GameProgress moved(float x, float y) {
        return new GameProgress(area, x, y, sageMet);
    }

    GameProgress entered(int nextArea, float x, float y) {
        return new GameProgress(nextArea, x, y, sageMet);
    }

    GameProgress metSage() {
        return new GameProgress(area, playerX, playerY, true);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
