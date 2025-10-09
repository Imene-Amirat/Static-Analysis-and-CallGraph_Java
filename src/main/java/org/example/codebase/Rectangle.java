package org.example.codebase;

public class Rectangle implements Shape {
    private Point origin;   // coin bas-gauche du rectangle
    private double width;   // largeur
    private double height;  // hauteur

    public Rectangle(Point origin, double width, double height) {
        this.origin = origin;
        this.width = width;
        this.height = height;
    }

    @Override
    public double area() {
        return width * height;
    }

    @Override
    public double perimeter() {
        return 2 * (width + height);
    }

    @Override
    public boolean contains(Point p) {
        return p.getX() >= origin.getX()
                && p.getY() >= origin.getY()
                && p.getX() <= origin.getX() + width
                && p.getY() <= origin.getY() + height;
    }

    @Override
    public void move(double dx, double dy) {
        origin.move(dx, dy); // on déplace le coin d’origine
    }

    @Override
    public void scale(double factor) {
        if (factor > 0) {
            width *= factor;
            height *= factor;
        }
    }

    // getters pour les métriques (facultatifs mais utiles)
    public double getWidth() { return width; }
    public double getHeight() { return height; }
}
