package com.wrongwiring.visualizer.service;

import com.wrongwiring.visualizer.model.CandidateMatch;
import com.wrongwiring.visualizer.model.ComplexNumber;
import com.wrongwiring.visualizer.model.ConnectionEntry;
import com.wrongwiring.visualizer.model.FaultDescriptor;
import com.wrongwiring.visualizer.model.Matrix3x3;
import com.wrongwiring.visualizer.model.NoiseEntry;
import com.wrongwiring.visualizer.model.Phase;
import com.wrongwiring.visualizer.model.PhaseVector;
import com.wrongwiring.visualizer.model.SimulationRequest;
import com.wrongwiring.visualizer.model.SimulationResponse;
import com.wrongwiring.visualizer.model.VectorEntry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Service
public class SimulationService {
    private static final List<Phase> PHASES = List.of(Phase.A, Phase.B, Phase.C);
    private static final List<List<Phase>> PHASE_PERMUTATIONS = List.of(
            List.of(Phase.A, Phase.B, Phase.C),
            List.of(Phase.A, Phase.C, Phase.B),
            List.of(Phase.B, Phase.A, Phase.C),
            List.of(Phase.B, Phase.C, Phase.A),
            List.of(Phase.C, Phase.A, Phase.B),
            List.of(Phase.C, Phase.B, Phase.A)
    );
    private static final double ZERO_THRESHOLD = 1e-6;
    private static final double W_AMP = 1.0;
    private static final double W_ANGLE = 0.35;

    private final Random random;

    public SimulationService() {
        this(new Random());
    }

    SimulationService(Random random) {
        this.random = random;
    }

    public SimulationResponse simulate(SimulationRequest request) {
        Map<Phase, Boolean> broken = phaseBooleanMap(request.broken());
        Map<Phase, Boolean> reversed = phaseBooleanMap(request.reversed());
        List<Phase> order = parseOrder(request.phaseOrder());

        FaultDescriptor trueFault = buildFaultDescriptor(broken, reversed, order);
        Matrix3x3 d = buildBrokenMatrix(broken);
        Matrix3x3 s = buildReverseMatrix(reversed);
        Matrix3x3 p = buildPermutationMatrix(order);
        Matrix3x3 m = buildTotalMatrix(d, s, p);

        PhaseVector x = buildVector(request);
        PhaseVector y0 = m.multiply(x);
        List<ConnectionEntry> connections = buildConnections(order, broken, reversed);
        NoiseResult noiseResult = applyNoise(y0, connections, request);
        PhaseVector y = noiseResult.vector();

        List<FaultDescriptor> candidates = enumerateFaultDescriptors();
        List<CandidateMatch> topMatches = buildTopMatches(candidates, trueFault, x, y, request.amplitude());

        boolean correct = d.isIdentity() && s.isIdentity() && p.isIdentity();
        String diagnosis = trueFault.description();
        String summary = correct
                ? "当前为正确接线，测量值与理论值一致。"
                : "当前接线存在错误：" + diagnosis + "。";

        return new SimulationResponse(
                correct,
                d.toArray(),
                s.toArray(),
                p.toArray(),
                m.toArray(),
                buildTheoreticalEntries(x),
                buildMeasuredEntries(y, connections),
                connections,
                diagnosis,
                summary,
                Instant.now(),
                buildMeasuredEntries(y0, connections),
                noiseResult.noise(),
                topMatches,
                candidates.size(),
                trueFault
        );
    }

    public Matrix3x3 buildBrokenMatrix(Map<Phase, Boolean> broken) {
        return Matrix3x3.diag(
                broken.getOrDefault(Phase.A, false) ? 0 : 1,
                broken.getOrDefault(Phase.B, false) ? 0 : 1,
                broken.getOrDefault(Phase.C, false) ? 0 : 1
        );
    }

    public Matrix3x3 buildReverseMatrix(Map<Phase, Boolean> reversed) {
        return Matrix3x3.diag(
                reversed.getOrDefault(Phase.A, false) ? -1 : 1,
                reversed.getOrDefault(Phase.B, false) ? -1 : 1,
                reversed.getOrDefault(Phase.C, false) ? -1 : 1
        );
    }

    public Matrix3x3 buildPermutationMatrix(List<Phase> order) {
        return Matrix3x3.permutation(order);
    }

    public Matrix3x3 buildTotalMatrix(Matrix3x3 d, Matrix3x3 s, Matrix3x3 p) {
        return p.multiply(s).multiply(d);
    }

    public FaultDescriptor buildFaultDescriptor(Map<Phase, Boolean> broken, Map<Phase, Boolean> reversed, List<Phase> order) {
        return new FaultDescriptor(
                phaseStringMap(broken),
                phaseStringMap(reversed),
                order.stream().map(Phase::name).toList(),
                buildFaultDescription(broken, reversed, order)
        );
    }

    public List<FaultDescriptor> enumerateFaultDescriptors() {
        List<FaultDescriptor> candidates = new ArrayList<>();
        for (int brokenMask = 0; brokenMask < 8; brokenMask++) {
            Map<Phase, Boolean> broken = maskToPhaseMap(brokenMask);
            for (int reversedMask = 0; reversedMask < 8; reversedMask++) {
                Map<Phase, Boolean> reversed = maskToPhaseMap(reversedMask);
                for (List<Phase> order : PHASE_PERMUTATIONS) {
                    candidates.add(buildFaultDescriptor(broken, reversed, order));
                }
            }
        }
        // Keep the all-normal and all-broken combinations visible so candidateCount stays explainable: 2^3 * 2^3 * 3! = 384.
        return candidates;
    }

    private PhaseVector buildVector(SimulationRequest request) {
        return new PhaseVector(
                fromRequestPolar(request, Phase.A),
                fromRequestPolar(request, Phase.B),
                fromRequestPolar(request, Phase.C)
        );
    }

    private ComplexNumber fromRequestPolar(SimulationRequest request, Phase phase) {
        double angle = request.phaseAngles().getOrDefault(phase.name(), defaultAngle(request.type(), phase));
        return ComplexNumber.fromPolar(request.amplitude(), angle);
    }

    private NoiseResult applyNoise(PhaseVector y0, List<ConnectionEntry> connections, SimulationRequest request) {
        Random activeRandom = request.seed() == null ? random : new Random(request.seed());
        List<ComplexNumber> values = new ArrayList<>();
        List<NoiseEntry> noiseEntries = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            ComplexNumber base = y0.get(i);
            double baseAmplitude = base.amplitude();
            double baseAngle = base.angleDegree();
            double amplitudeDelta = 0;
            double angleDelta = 0;
            ComplexNumber noisy = base;

            if (request.noiseEnabled() && baseAmplitude >= ZERO_THRESHOLD) {
                double amplitudeRange = baseAmplitude * request.amplitudeNoisePercent() / 100.0;
                amplitudeDelta = randomBetween(activeRandom, -amplitudeRange, amplitudeRange);
                angleDelta = randomBetween(activeRandom, -request.angleNoiseDegree(), request.angleNoiseDegree());
                double noisyAmplitude = Math.max(0, baseAmplitude + amplitudeDelta);
                double noisyAngle = ComplexNumber.normalizeAngle(baseAngle + angleDelta);
                noisy = ComplexNumber.fromPolar(noisyAmplitude, noisyAngle);
            }

            ConnectionEntry connection = connections.get(i);
            values.add(noisy);
            noiseEntries.add(new NoiseEntry(
                    connection.channel(),
                    connection.source(),
                    ComplexNumber.round(amplitudeDelta),
                    ComplexNumber.round(angleDelta),
                    ComplexNumber.round(baseAmplitude),
                    ComplexNumber.round(baseAngle),
                    ComplexNumber.round(noisy.amplitude()),
                    ComplexNumber.round(noisy.angleDegree())
            ));
        }

        return new NoiseResult(new PhaseVector(values.get(0), values.get(1), values.get(2)), noiseEntries);
    }

    private List<CandidateMatch> buildTopMatches(
            List<FaultDescriptor> candidates,
            FaultDescriptor trueFault,
            PhaseVector x,
            PhaseVector y,
            double baseAmplitude
    ) {
        List<CandidateEvaluation> evaluations = candidates.stream()
                .map(candidate -> evaluateCandidate(candidate, trueFault, x, y, baseAmplitude))
                .sorted(Comparator
                        .comparingDouble(CandidateEvaluation::distance)
                        .thenComparing(evaluation -> !evaluation.trueFaultMatch())
                        .thenComparingInt(CandidateEvaluation::faultWeight)
                        .thenComparing(evaluation -> evaluation.fault().description()))
                .toList();

        List<CandidateMatch> matches = new ArrayList<>();
        for (int i = 0; i < Math.min(3, evaluations.size()); i++) {
            CandidateEvaluation evaluation = evaluations.get(i);
            matches.add(new CandidateMatch(
                    i + 1,
                    evaluation.fault(),
                    evaluation.d().toArray(),
                    evaluation.s().toArray(),
                    evaluation.p().toArray(),
                    evaluation.m().toArray(),
                    evaluation.predictedVector(),
                    evaluation.distance(),
                    evaluation.similarity(),
                    evaluation.explanation(),
                    evaluation.trueFaultMatch()
            ));
        }
        return matches;
    }

    private CandidateEvaluation evaluateCandidate(
            FaultDescriptor candidate,
            FaultDescriptor trueFault,
            PhaseVector x,
            PhaseVector y,
            double baseAmplitude
    ) {
        Map<Phase, Boolean> broken = phaseBooleanMap(candidate.broken());
        Map<Phase, Boolean> reversed = phaseBooleanMap(candidate.reversed());
        List<Phase> order = parseOrder(candidate.phaseOrder());
        Matrix3x3 d = buildBrokenMatrix(broken);
        Matrix3x3 s = buildReverseMatrix(reversed);
        Matrix3x3 p = buildPermutationMatrix(order);
        Matrix3x3 m = buildTotalMatrix(d, s, p);
        PhaseVector predicted = m.multiply(x);
        double distance = distance(y, predicted, Math.max(baseAmplitude, ZERO_THRESHOLD));
        double similarity = 1.0 / (1.0 + distance);
        List<ConnectionEntry> connections = buildConnections(order, broken, reversed);
        return new CandidateEvaluation(
                candidate,
                d,
                s,
                p,
                m,
                buildMeasuredEntries(predicted, connections),
                distance,
                similarity,
                candidate.description() + "；" + connectionExplanation(connections),
                sameFault(candidate, trueFault),
                faultWeight(candidate)
        );
    }

    private double distance(PhaseVector measured, PhaseVector predicted, double baseAmplitude) {
        double total = 0;
        for (int i = 0; i < 3; i++) {
            ComplexNumber actual = measured.get(i);
            ComplexNumber expected = predicted.get(i);
            double ampError = Math.abs(actual.amplitude() - expected.amplitude()) / baseAmplitude;
            double angleError = angleError(actual, expected) / 180.0;
            total += W_AMP * ampError + W_ANGLE * angleError;
        }
        return total;
    }

    private double angleError(ComplexNumber actual, ComplexNumber expected) {
        boolean actualZero = actual.amplitude() < ZERO_THRESHOLD;
        boolean expectedZero = expected.amplitude() < ZERO_THRESHOLD;
        if (actualZero || expectedZero) {
            return 0;
        }
        return Math.abs(angleDiff(actual.angleDegree(), expected.angleDegree()));
    }

    private double angleDiff(double a, double b) {
        return ComplexNumber.normalizeAngle(a - b);
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

    private String buildFaultDescription(Map<Phase, Boolean> broken, Map<Phase, Boolean> reversed, List<Phase> order) {
        List<String> issues = new ArrayList<>();
        for (Phase phase : PHASES) {
            if (broken.get(phase)) {
                issues.add(phase.name() + " 相断线");
            }
            if (reversed.get(phase)) {
                issues.add(phase.name() + " 相反接");
            }
        }
        if (!order.equals(PHASES)) {
            issues.add(connectionExplanation(buildConnections(order, broken, reversed)));
        } else if (!issues.isEmpty()) {
            issues.add("不换相");
        }
        return issues.stream().filter(Objects::nonNull).reduce((left, right) -> left + "，" + right).orElse("三相接线正常");
    }

    private String connectionExplanation(List<ConnectionEntry> connections) {
        return connections.stream()
                .map(connection -> connection.channel() + "读取" + connection.source() + "相")
                .reduce((left, right) -> left + "，" + right)
                .orElse("通道顺序未知");
    }

    private boolean sameFault(FaultDescriptor left, FaultDescriptor right) {
        return left.broken().equals(right.broken())
                && left.reversed().equals(right.reversed())
                && left.phaseOrder().equals(right.phaseOrder());
    }

    private int faultWeight(FaultDescriptor fault) {
        int weight = 0;
        for (boolean value : fault.broken().values()) {
            if (value) {
                weight++;
            }
        }
        for (boolean value : fault.reversed().values()) {
            if (value) {
                weight++;
            }
        }
        if (!fault.phaseOrder().equals(List.of("A", "B", "C"))) {
            weight++;
        }
        return weight;
    }

    private Map<Phase, Boolean> phaseBooleanMap(Map<String, Boolean> raw) {
        Map<Phase, Boolean> result = new LinkedHashMap<>();
        result.put(Phase.A, raw.getOrDefault("A", false));
        result.put(Phase.B, raw.getOrDefault("B", false));
        result.put(Phase.C, raw.getOrDefault("C", false));
        return result;
    }

    private Map<String, Boolean> phaseStringMap(Map<Phase, Boolean> raw) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Phase phase : PHASES) {
            result.put(phase.name(), raw.getOrDefault(phase, false));
        }
        return result;
    }

    private Map<Phase, Boolean> maskToPhaseMap(int mask) {
        Map<Phase, Boolean> result = new LinkedHashMap<>();
        for (Phase phase : PHASES) {
            result.put(phase, (mask & (1 << phase.ordinal())) != 0);
        }
        return result;
    }

    private List<Phase> parseOrder(List<String> rawOrder) {
        List<Phase> order = rawOrder.stream().map(Phase::from).toList();
        Set<Phase> unique = new LinkedHashSet<>(order);
        if (order.size() != 3 || unique.size() != 3 || !unique.containsAll(PHASES)) {
            throw new IllegalArgumentException("phaseOrder must contain A, B, C exactly once");
        }
        return order;
    }

    private double randomBetween(Random activeRandom, double min, double max) {
        return min + (max - min) * activeRandom.nextDouble();
    }

    private record NoiseResult(PhaseVector vector, List<NoiseEntry> noise) {
    }

    private record CandidateEvaluation(
            FaultDescriptor fault,
            Matrix3x3 d,
            Matrix3x3 s,
            Matrix3x3 p,
            Matrix3x3 m,
            List<VectorEntry> predictedVector,
            double distance,
            double similarity,
            String explanation,
            boolean trueFaultMatch,
            int faultWeight
    ) {
    }
}
