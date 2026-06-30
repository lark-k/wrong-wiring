package com.wrongwiring.visualizer.model;

import java.util.List;

public record CandidateMatch(
        int rank,
        FaultDescriptor fault,
        double[][] D,
        double[][] S,
        double[][] P,
        double[][] M,
        List<VectorEntry> predictedVector,
        double distance,
        double similarity,
        String explanation,
        boolean trueFaultMatch
) {
}
