package com.wrongwiring.visualizer.model;

import java.util.Locale;

public final class ComplexNumber {
    private static final double EPSILON = 1e-9;

    private final double real;
    private final double imag;

    public ComplexNumber(double real, double imag) {
        this.real = normalizeZero(real);
        this.imag = normalizeZero(imag);
    }

    public static ComplexNumber zero() {
        return new ComplexNumber(0, 0);
    }

    public static ComplexNumber fromPolar(double amplitude, double angleDegree) {
        double radians = Math.toRadians(angleDegree);
        return new ComplexNumber(amplitude * Math.cos(radians), amplitude * Math.sin(radians));
    }

    public double real() {
        return real;
    }

    public double imag() {
        return imag;
    }

    public ComplexNumber add(ComplexNumber other) {
        return new ComplexNumber(real + other.real, imag + other.imag);
    }

    public ComplexNumber multiply(double scalar) {
        return new ComplexNumber(real * scalar, imag * scalar);
    }

    public double amplitude() {
        return Math.hypot(real, imag);
    }

    public double angleDegree() {
        if (amplitude() < EPSILON) {
            return 0;
        }
        return normalizeAngle(Math.toDegrees(Math.atan2(imag, real)));
    }

    public String formatComplex() {
        if (amplitude() < EPSILON) {
            return "0";
        }
        if (Math.abs(imag) < EPSILON) {
            return formatNumber(real);
        }
        if (Math.abs(real) < EPSILON) {
            return (imag < 0 ? "-j" : "j") + formatNumber(Math.abs(imag));
        }
        return "%s %s j%s".formatted(
                formatNumber(real),
                imag < 0 ? "-" : "+",
                formatNumber(Math.abs(imag))
        );
    }

    public String formatPolar() {
        if (amplitude() < EPSILON) {
            return "0";
        }
        return "%s∠%s°".formatted(formatNumber(amplitude()), formatNumber(angleDegree()));
    }

    public static double normalizeAngle(double angle) {
        double normalized = angle % 360.0;
        if (normalized > 180.0) {
            normalized -= 360.0;
        }
        if (normalized <= -180.0) {
            normalized += 360.0;
        }
        return normalizeZero(normalized);
    }

    public static double round(double value) {
        return normalizeZero(Math.round(value * 100.0) / 100.0);
    }

    public static String formatNumber(double value) {
        double rounded = round(value);
        if (Math.abs(rounded - Math.rint(rounded)) < EPSILON) {
            return String.format(Locale.US, "%.0f", rounded);
        }
        return String.format(Locale.US, "%.2f", rounded).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static double normalizeZero(double value) {
        return Math.abs(value) < EPSILON ? 0 : value;
    }
}
