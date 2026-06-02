package com.chaseschwartz.extractcraft.raid.inventory;

public record GridMoveResult(boolean success, String message) {
    public static GridMoveResult success(String message) {
        return new GridMoveResult(true, message);
    }

    public static GridMoveResult failure(String message) {
        return new GridMoveResult(false, message);
    }
}
