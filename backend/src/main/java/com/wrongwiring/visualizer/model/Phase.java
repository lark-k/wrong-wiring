package com.wrongwiring.visualizer.model;

public enum Phase {
    A, B, C;

    public static Phase from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Phase cannot be null");
        }
        return Phase.valueOf(value.trim().toUpperCase());
    }
}
