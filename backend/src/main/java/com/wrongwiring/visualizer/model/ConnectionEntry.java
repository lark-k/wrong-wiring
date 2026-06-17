package com.wrongwiring.visualizer.model;

public record ConnectionEntry(
        String channel,
        String source,
        boolean broken,
        boolean reversed,
        boolean swapped,
        String status
) {
}
