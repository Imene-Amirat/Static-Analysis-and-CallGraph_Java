package org.example.codebase;

public class Point {
    private double x;   // Coordonnée X
    private double y;   // Coordonnée Y

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }  // Accès à X
    public double getY() { return y; }  // Accès à Y

    public void move(double dx, double dy) {
        this.x += dx;   // on ajoute dx à x
        this.y += dy;   // on ajoute dy à y
    }

    public double distanceTo(Point other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
