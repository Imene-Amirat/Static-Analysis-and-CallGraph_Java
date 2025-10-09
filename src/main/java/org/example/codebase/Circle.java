package org.example.codebase;

public class Circle implements Shape {
    private Point center;  // centre du cercle
    private double radius; // rayon

    public Circle(Point center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }

    @Override
    public double perimeter() {
        return 2 * Math.PI * radius;
    }

    @Override
    public boolean contains(Point p) {
        double distance = center.distanceTo(p);
        return distance <= radius;
    }

    @Override
    public void move(double dx, double dy) {
        center.move(dx, dy);
    }

    @Override
    public void scale(double factor) {
        if (factor > 0) {
            radius *= factor;
        }
    }

    // Getter pour la métrique
    public double getRadius() { return radius; }
}
