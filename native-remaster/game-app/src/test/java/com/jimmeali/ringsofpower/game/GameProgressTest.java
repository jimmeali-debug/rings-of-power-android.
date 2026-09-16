package com.jimmeali.ringsofpower.game;

public final class GameProgressTest {
    public static void main(String[] args) {
        GameProgress fresh = GameProgress.newGame();
        require(fresh.area == GameProgress.AREA_BEGINNING, "new area");
        require(!fresh.sageMet, "new quest state");
        GameProgress crossed = fresh.entered(GameProgress.AREA_SAGE, 0.65f, 3.0f);
        require(crossed.area == GameProgress.AREA_SAGE, "area transition");
        require(crossed.playerX == 0.65f, "transition position");
        GameProgress met = crossed.metSage();
        require(met.sageMet, "quest progression");
        GameProgress clamped = new GameProgress(99, -10f, 99f, false);
        require(clamped.area == GameProgress.AREA_BEGINNING, "invalid area fallback");
        require(clamped.playerX == 0.5f && clamped.playerY == 7.5f, "position clamp");
        System.out.println("GameProgressTest passed");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
