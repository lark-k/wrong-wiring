package com.wrongwiring.visualizer.model;

import java.time.Instant;
import java.util.List;

public record SimulationResponse(
        boolean correctWiring,
        double[][] D,
        double[][] S,
        double[][] P,
        double[][] M,
        List<VectorEntry> x,
        List<VectorEntry> y,
        List<ConnectionEntry> connections,
        String diagnosis,
        String summary,
        Instant calculatedAt,
        List<VectorEntry> y0,
        List<NoiseEntry> noise,
        List<CandidateMatch> topMatches,
        int candidateCount,
        FaultDescriptor trueFault
) {
}
