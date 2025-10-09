package org.example.codebase;

public interface Shape {
    double area();           // Calcule la surface de la forme
    double perimeter();      // Calcule le périmètre de la forme
    boolean contains(Point p); // Vérifie si un point est à l'intérieur de la forme
    void move(double dx, double dy); // Déplace la forme de dx, dy
    void scale(double factor);       // Agrandit ou réduit la forme
}
