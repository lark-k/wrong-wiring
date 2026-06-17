package com.wrongwiring.visualizer.model;

import java.util.Arrays;
import java.util.List;

public final class PhaseVector {
    private final ComplexNumber a;
    private final ComplexNumber b;
    private final ComplexNumber c;

    public PhaseVector(ComplexNumber a, ComplexNumber b, ComplexNumber c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public ComplexNumber get(Phase phase) {
        return switch (phase) {
            case A -> a;
            case B -> b;
            case C -> c;
        };
    }

    public ComplexNumber get(int index) {
        return switch (index) {
            case 0 -> a;
            case 1 -> b;
            case 2 -> c;
            default -> throw new IllegalArgumentException("Phase index must be 0..2");
        };
    }

    public List<ComplexNumber> values() {
        return Arrays.asList(a, b, c);
    }
}
