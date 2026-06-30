package com.wrongwiring.visualizer.model;

public record NoiseEntry(
        String channel,
        String source,
        double amplitudeDelta,
        double angleDelta,
        double baseAmplitude,
        double baseAngle,
        double noisyAmplitude,
        double noisyAngle
) {
}
