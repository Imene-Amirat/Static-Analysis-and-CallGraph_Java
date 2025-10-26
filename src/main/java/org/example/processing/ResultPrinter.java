package org.example.processing;

import java.util.Map;

public class ResultPrinter {

    // Affiche les statistiques globales du projet analysé
    public static void print(StatisticsService.ProjectMetrics pm, int thresholdX) {
        System.out.println("=== Résumé du projet ===");
        System.out.println("Fichiers analysés : " + pm.totalFiles);
        System.out.println("Packages totaux   : " + pm.totalPackages);
        System.out.println("Classes totales   : " + pm.totalClasses);
        System.out.println("Méthodes totales  : " + pm.totalMethods);
        System.out.println("Attributs totaux  : " + pm.totalFields);
        System.out.println("LOC totales       : " + pm.totalLoc);
        System.out.println("Moy. méthodes/Classe : " + String.format("%.2f", pm.avgMethodsPerClass));
        System.out.println("Moy. attributs/Classe : " + String.format("%.2f", pm.avgFieldsPerClass));
        System.out.println("Moy. LOC/Méthode     : " + String.format("%.2f", pm.avgLocPerMethod));
        System.out.println("Max paramètres (méthode) : " + pm.maxParams);

        System.out.println("\nTop 10% classes par méthodes : " + pm.top10pctClassesByMethods);
        System.out.println("Top 10% classes par attributs : " + pm.top10pctClassesByFields);
        System.out.println("Intersection des deux tops    : " + pm.intersectionTop);

        System.out.println("\nClasses avec plus de " + thresholdX + " méthodes : " + pm.classesWithMoreThanXMethods);

        System.out.println("\nTop 10% méthodes par LOC (global) :");
        for (Map.Entry<String,Integer> e : pm.longestMethods.entrySet()) {
            System.out.println("  " + e.getKey() + " -> " + e.getValue() + " LOC");
        }

        System.out.println("\nTop 10% méthodes par LOC (par classe) :");
        pm.longestMethodsPerClass.forEach((cls, map) -> {
            System.out.println("  Classe: " + cls);
            map.forEach((k,v) -> System.out.println("    " + k + " -> " + v + " LOC"));
        });
    }
}

