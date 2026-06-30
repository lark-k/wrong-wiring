package com.wrongwiring.visualizer.service;

import com.wrongwiring.visualizer.model.Phase;
import com.wrongwiring.visualizer.model.SimulationRequest;
import com.wrongwiring.visualizer.model.SimulationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationServiceTest {
    private final SimulationService service = new SimulationService(new Random(7));

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
        assertThat(response.y0().get(0).polar()).isEqualTo(response.x().get(0).polar());
        assertThat(response.y().get(0).polar()).isEqualTo(response.x().get(0).polar());
    }

    @Test
    void noNoiseTrueFaultIsTop1() {
        SimulationResponse response = service.simulate(defaultRequest(
                Map.of("A", false, "B", true, "C", false),
                Map.of("A", true, "B", false, "C", false),
                List.of("B", "A", "C")
        ));

        assertThat(response.topMatches()).hasSize(3);
        assertThat(response.topMatches().get(0).trueFaultMatch()).isTrue();
        assertThat(response.topMatches().get(0).distance()).isZero();
    }

    @Test
    void smallNoiseKeepsTrueFaultInTop3() {
        SimulationResponse response = service.simulate(defaultRequest(
                Map.of("A", false, "B", true, "C", false),
                Map.of("A", true, "B", false, "C", true),
                List.of("B", "A", "C"),
                true,
                0.4,
                0.2,
                42L
        ));

        assertThat(response.topMatches())
                .anySatisfy(match -> assertThat(match.trueFaultMatch()).isTrue());
    }

    @Test
    void aPhaseReverseRotatesMeasuredPhaseBy180Degrees() {
        SimulationResponse response = service.simulate(defaultRequest(
                Map.of("A", false, "B", false, "C", false),
                Map.of("A", true, "B", false, "C", false),
                List.of("A", "B", "C")
        ));

        assertThat(response.S()[0][0]).isEqualTo(-1);
        assertThat(response.y0().get(0).polar()).isEqualTo("220∠180°");
        assertThat(response.y().get(0).polar()).isEqualTo("220∠180°");
    }

    @Test
    void bPhaseBrokenReturnsZeroMeasuredValue() {
        SimulationResponse response = service.simulate(defaultRequest(
                Map.of("A", false, "B", true, "C", false),
                Map.of("A", false, "B", false, "C", false),
                List.of("A", "B", "C"),
                true,
                5,
                5,
                99L
        ));

        assertThat(response.D()[1][1]).isZero();
        assertThat(response.y0().get(1).polar()).isEqualTo("0");
        assertThat(response.y().get(1).polar()).isEqualTo("0");
        assertThat(response.noise().get(1).amplitudeDelta()).isZero();
        assertThat(response.noise().get(1).angleDelta()).isZero();
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

    @Test
    void candidateEnumerationContainsAllBrokenReverseAndPermutationCombinations() {
        assertThat(service.enumerateFaultDescriptors()).hasSize(384);
    }

    @Test
    void buildMatrixMethodsAreReusable() {
        assertThat(service.buildBrokenMatrix(Map.of(Phase.A, false, Phase.B, true, Phase.C, false)).toArray())
                .isDeepEqualTo(new double[][]{
                        {1, 0, 0},
                        {0, 0, 0},
                        {0, 0, 1}
                });
        assertThat(service.buildPermutationMatrix(List.of(Phase.B, Phase.A, Phase.C)).toArray())
                .isDeepEqualTo(new double[][]{
                        {0, 1, 0},
                        {1, 0, 0},
                        {0, 0, 1}
                });
    }

    private SimulationRequest defaultRequest(Map<String, Boolean> broken, Map<String, Boolean> reversed, List<String> order) {
        return defaultRequest(broken, reversed, order, false, 1.0, 0.5, null);
    }

    private SimulationRequest defaultRequest(
            Map<String, Boolean> broken,
            Map<String, Boolean> reversed,
            List<String> order,
            boolean noiseEnabled,
            double amplitudeNoisePercent,
            double angleNoiseDegree,
            Long seed
    ) {
        return new SimulationRequest(
                "voltage",
                220,
                Map.of("A", 0.0, "B", -120.0, "C", 120.0),
                broken,
                reversed,
                order,
                noiseEnabled,
                amplitudeNoisePercent,
                angleNoiseDegree,
                seed
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
