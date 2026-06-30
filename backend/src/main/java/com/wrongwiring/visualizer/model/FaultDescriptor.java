package com.wrongwiring.visualizer.model;

import java.util.List;
import java.util.Map;

public record FaultDescriptor(
        Map<String, Boolean> broken,
        Map<String, Boolean> reversed,
        List<String> phaseOrder,
        String description
) {
}
