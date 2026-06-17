package com.wrongwiring.visualizer.model;

public record VectorEntry(
        String phase,
        String channel,
        String source,
        double real,
        double imag,
        double amplitude,
        double angle,
        String complex,
        String polar,
        boolean broken,
        boolean reversed
) {
}
