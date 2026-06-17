package com.wrongwiring.visualizer.model;

import java.util.Arrays;
import java.util.List;

public final class Matrix3x3 {
    private static final int SIZE = 3;
    private final double[][] values;

    public Matrix3x3(double[][] values) {
        if (values.length != SIZE || Arrays.stream(values).anyMatch(row -> row.length != SIZE)) {
            throw new IllegalArgumentException("Matrix3x3 requires a 3x3 array");
        }
        this.values = new double[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(values[row], 0, this.values[row], 0, SIZE);
        }
    }

    public static Matrix3x3 identity() {
        return diag(1, 1, 1);
    }

    public static Matrix3x3 diag(double a, double b, double c) {
        return new Matrix3x3(new double[][]{
                {a, 0, 0},
                {0, b, 0},
                {0, 0, c}
        });
    }

    public static Matrix3x3 permutation(List<Phase> order) {
        if (order.size() != SIZE) {
            throw new IllegalArgumentException("Phase order must contain three phases");
        }
        double[][] matrix = new double[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            int sourceIndex = order.get(row).ordinal();
            matrix[row][sourceIndex] = 1;
        }
        return new Matrix3x3(matrix);
    }

    public Matrix3x3 multiply(Matrix3x3 other) {
        double[][] result = new double[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                double sum = 0;
                for (int k = 0; k < SIZE; k++) {
                    sum += values[row][k] * other.values[k][col];
                }
                result[row][col] = sum;
            }
        }
        return new Matrix3x3(result);
    }

    public PhaseVector multiply(PhaseVector vector) {
        ComplexNumber[] result = new ComplexNumber[SIZE];
        for (int row = 0; row < SIZE; row++) {
            ComplexNumber sum = ComplexNumber.zero();
            for (int col = 0; col < SIZE; col++) {
                sum = sum.add(vector.get(col).multiply(values[row][col]));
            }
            result[row] = sum;
        }
        return new PhaseVector(result[0], result[1], result[2]);
    }

    public double[][] toArray() {
        double[][] copy = new double[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(values[row], 0, copy[row], 0, SIZE);
        }
        return copy;
    }

    public boolean isIdentity() {
        return Arrays.deepEquals(toArray(), identity().toArray());
    }
}
