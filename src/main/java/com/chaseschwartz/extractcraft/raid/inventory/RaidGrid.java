package com.chaseschwartz.extractcraft.raid.inventory;

public record RaidGrid(int width, int height) {
    public int capacity() {
        return Math.max(0, width) * Math.max(0, height);
    }
}
