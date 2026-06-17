package com.wrongwiring.visualizer.service;

import com.wrongwiring.visualizer.model.SimulationRequest;
import com.wrongwiring.visualizer.model.SimulationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationServiceTest {
    private final SimulationService service = new SimulationService();

    @Test
    void correctWiringReturnsIdentityAndSameVectors() {
        SimulationResponse response = service.simulate(defaultRequest(
                Map.of("A", false, "B", false, "C", false),
                Map.of("A", false, "B", false, "C", false),
                List.of("A", "B", "C")
        ));

        assertThat(response.correctWiring()).isTrue();
        assertThat(response.D()).isDeepEqualTo(identity());
        assertThat(response.S()).isDeepEqualTo(identity());
        assertThat(response.P()).isDeepEqualTo(identity());
        assertThat(response.M()).isDeepEqualTo(identity());
        assertThat(response.y().get(0).polar()).isEqualTo(response.x().get(0).polar());
    }

    @Test
    void aPhaseReverseRotatesMeasuredPhaseBy180Degrees() {
        SimulationResponse response = service.simulate(defaultRequest(
                Map.of("A", false, "B", false, "C", false),
                Map.of("A", true, "B", false, "C", false),
                List.of("A", "B", "C")
        ));

        assertThat(response.S()[0][0]).isEqualTo(-1);
        assertThat(response.y().get(0).polar()).isEqualTo("220∠180°");
    }

    @Test
    void bPhaseBrokenReturnsZeroMeasuredValue() {
        SimulationResponse response = service.simulate(defaultRequest(
                Map.of("A", false, "B", true, "C", false),
                Map.of("A", false, "B", false, "C", false),
                List.of("A", "B", "C")
        ));

        assertThat(response.D()[1][1]).isZero();
        assertThat(response.y().get(1).polar()).isEqualTo("0");
    }

    @Test
    void abSwapCreatesPermutationMatrix() {
        SimulationResponse response = service.simulate(defaultRequest(
                Map.of("A", false, "B", false, "C", false),
                Map.of("A", false, "B", false, "C", false),
                List.of("B", "A", "C")
        ));

        assertThat(response.P()).isDeepEqualTo(new double[][]{
                {0, 1, 0},
                {1, 0, 0},
                {0, 0, 1}
        });
        assertThat(response.y().get(0).source()).isEqualTo("B");
    }

    private SimulationRequest defaultRequest(Map<String, Boolean> broken, Map<String, Boolean> reversed, List<String> order) {
        return new SimulationRequest(
                "voltage",
                220,
                Map.of("A", 0.0, "B", -120.0, "C", 120.0),
                broken,
                reversed,
                order,
                false,
                1.0,
                0.5
        );
    }

    private double[][] identity() {
        return new double[][]{
                {1, 0, 0},
                {0, 1, 0},
                {0, 0, 1}
        };
    }
}
