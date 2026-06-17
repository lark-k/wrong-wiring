package com.wrongwiring.visualizer.controller;

import com.wrongwiring.visualizer.model.SimulationRequest;
import com.wrongwiring.visualizer.model.SimulationResponse;
import com.wrongwiring.visualizer.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SimulationController {
    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/simulate")
    public SimulationResponse simulate(@Valid @RequestBody SimulationRequest request) {
        return simulationService.simulate(request);
    }
}
