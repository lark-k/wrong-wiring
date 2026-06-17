package com.wrongwiring.visualizer.service;

import com.wrongwiring.visualizer.model.ComplexNumber;
import com.wrongwiring.visualizer.model.ConnectionEntry;
import com.wrongwiring.visualizer.model.Matrix3x3;
import com.wrongwiring.visualizer.model.Phase;
import com.wrongwiring.visualizer.model.PhaseVector;
import com.wrongwiring.visualizer.model.SimulationRequest;
import com.wrongwiring.visualizer.model.SimulationResponse;
import com.wrongwiring.visualizer.model.VectorEntry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
public class SimulationService {
    private static final List<Phase> PHASES = List.of(Phase.A, Phase.B, Phase.C);
    private static final Random RANDOM = new Random();

    public SimulationResponse simulate(SimulationRequest request) {
        Map<Phase, Boolean> broken = phaseBooleanMap(request.broken());
        Map<Phase, Boolean> reversed = phaseBooleanMap(request.reversed());
        List<Phase> order = parseOrder(request.phaseOrder());

        Matrix3x3 d = Matrix3x3.diag(
                broken.get(Phase.A) ? 0 : 1,
                broken.get(Phase.B) ? 0 : 1,
                broken.get(Phase.C) ? 0 : 1
        );
        Matrix3x3 s = Matrix3x3.diag(
                reversed.get(Phase.A) ? -1 : 1,
                reversed.get(Phase.B) ? -1 : 1,
                reversed.get(Phase.C) ? -1 : 1
        );
        Matrix3x3 p = Matrix3x3.permutation(order);
        Matrix3x3 m = p.multiply(s).multiply(d);

        PhaseVector theoretical = buildVector(request);
        PhaseVector measured = m.multiply(theoretical);

        boolean correct = d.isIdentity() && s.isIdentity() && p.isIdentity();
        List<ConnectionEntry> connections = buildConnections(order, broken, reversed);
        String diagnosis = buildDiagnosis(broken, reversed, order);
        String summary = correct
                ? "当前为正确接线，测量值与理论值一致。"
                : "当前接线存在错误：" + diagnosis + "。";

        return new SimulationResponse(
                correct,
                d.toArray(),
                s.toArray(),
                p.toArray(),
                m.toArray(),
                buildTheoreticalEntries(theoretical),
                buildMeasuredEntries(measured, connections),
                connections,
                diagnosis,
                summary,
                Instant.now()
        );
    }

    private PhaseVector buildVector(SimulationRequest request) {
        return new PhaseVector(
                fromRequestPolar(request, Phase.A),
                fromRequestPolar(request, Phase.B),
                fromRequestPolar(request, Phase.C)
        );
    }

    private ComplexNumber fromRequestPolar(SimulationRequest request, Phase phase) {
        double amplitude = request.amplitude();
        double angle = request.phaseAngles().getOrDefault(phase.name(), defaultAngle(request.type(), phase));
        if (request.noiseEnabled()) {
            double ampDelta = amplitude * request.amplitudeNoisePercent() / 100.0;
            amplitude += randomBetween(-ampDelta, ampDelta);
            angle += randomBetween(-request.angleNoiseDegree(), request.angleNoiseDegree());
        }
        return ComplexNumber.fromPolar(amplitude, angle);
    }

    private double defaultAngle(String type, Phase phase) {
        boolean current = "current".equalsIgnoreCase(type);
        return switch (phase) {
            case A -> current ? -30 : 0;
            case B -> current ? -150 : -120;
            case C -> current ? 90 : 120;
        };
    }

    private List<VectorEntry> buildTheoreticalEntries(PhaseVector vector) {
        List<VectorEntry> entries = new ArrayList<>();
        for (Phase phase : PHASES) {
            ComplexNumber value = vector.get(phase);
            entries.add(vectorEntry(phase.name(), null, phase.name(), value, false, false));
        }
        return entries;
    }

    private List<VectorEntry> buildMeasuredEntries(PhaseVector vector, List<ConnectionEntry> connections) {
        List<VectorEntry> entries = new ArrayList<>();
        for (int i = 0; i < connections.size(); i++) {
            ConnectionEntry connection = connections.get(i);
            entries.add(vectorEntry(null, connection.channel(), connection.source(), vector.get(i), connection.broken(), connection.reversed()));
        }
        return entries;
    }

    private VectorEntry vectorEntry(String phase, String channel, String source, ComplexNumber value, boolean broken, boolean reversed) {
        return new VectorEntry(
                phase,
                channel,
                source,
                ComplexNumber.round(value.real()),
                ComplexNumber.round(value.imag()),
                ComplexNumber.round(value.amplitude()),
                ComplexNumber.round(value.angleDegree()),
                value.formatComplex(),
                value.formatPolar(),
                broken,
                reversed
        );
    }

    private List<ConnectionEntry> buildConnections(List<Phase> order, Map<Phase, Boolean> broken, Map<Phase, Boolean> reversed) {
        List<ConnectionEntry> connections = new ArrayList<>();
        for (int channelIndex = 0; channelIndex < order.size(); channelIndex++) {
            Phase source = order.get(channelIndex);
            boolean sourceBroken = broken.get(source);
            boolean sourceReversed = reversed.get(source);
            boolean swapped = source.ordinal() != channelIndex;
            List<String> parts = new ArrayList<>();
            if (swapped) {
                parts.add("换相");
            }
            if (sourceBroken) {
                parts.add("断线");
            }
            if (sourceReversed) {
                parts.add("反接");
            }
            connections.add(new ConnectionEntry(
                    "通道" + (channelIndex + 1),
                    source.name(),
                    sourceBroken,
                    sourceReversed,
                    swapped,
                    parts.isEmpty() ? "正常" : String.join(" / ", parts)
            ));
        }
        return connections;
    }

    private String buildDiagnosis(Map<Phase, Boolean> broken, Map<Phase, Boolean> reversed, List<Phase> order) {
        List<String> issues = new ArrayList<>();
        for (Phase phase : PHASES) {
            if (broken.get(phase)) {
                issues.add(phase.name() + "相断线");
            }
            if (reversed.get(phase)) {
                issues.add(phase.name() + "相反接");
            }
        }
        if (!order.equals(PHASES)) {
            issues.add("换相顺序为 " + order.stream().map(Phase::name).reduce("", String::concat));
        }
        return issues.isEmpty() ? "A相正常，B相正常，C相正常" : String.join("，", issues);
    }

    private Map<Phase, Boolean> phaseBooleanMap(Map<String, Boolean> raw) {
        return Map.of(
                Phase.A, raw.getOrDefault("A", false),
                Phase.B, raw.getOrDefault("B", false),
                Phase.C, raw.getOrDefault("C", false)
        );
    }

    private List<Phase> parseOrder(List<String> rawOrder) {
        List<Phase> order = rawOrder.stream().map(Phase::from).toList();
        Set<Phase> unique = new LinkedHashSet<>(order);
        if (order.size() != 3 || unique.size() != 3 || !unique.containsAll(PHASES)) {
            throw new IllegalArgumentException("phaseOrder must contain A, B, C exactly once");
        }
        return order;
    }

    private double randomBetween(double min, double max) {
        return min + (max - min) * RANDOM.nextDouble();
    }
}
