package com.wrongwiring.visualizer.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

public record SimulationRequest(
        @NotBlank String type,
        @Positive double amplitude,
        @NotNull Map<String, Double> phaseAngles,
        @NotNull Map<String, Boolean> broken,
        @NotNull Map<String, Boolean> reversed,
        @NotNull List<String> phaseOrder,
        boolean noiseEnabled,
        double amplitudeNoisePercent,
        double angleNoiseDegree
) {
}
