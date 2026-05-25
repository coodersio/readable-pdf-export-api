package io.cooders.readablepdf.dto;

public record RgbaColor(double r, double g, double b, double a) {

    public static RgbaColor black() {
        return new RgbaColor(0, 0, 0, 1);
    }

    public float red() {
        return clamp(r);
    }

    public float green() {
        return clamp(g);
    }

    public float blue() {
        return clamp(b);
    }

    public float alpha() {
        return clamp(a);
    }

    private float clamp(double value) {
        if (Double.isNaN(value)) {
            return 1f;
        }

        return (float) Math.max(0, Math.min(1, value));
    }
}

