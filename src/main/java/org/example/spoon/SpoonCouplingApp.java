package org.example.spoon;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.*;

import static org.example.spoon.SpoonCallGraphExtractor.*;

public class SpoonCouplingApp {

    public static void main(String[] args) throws Exception {
        // 1) Racine des sources (auto si non fourni)
        Path srcRoot = detectSrcRoot(args);

        // 2) Modèle Spoon + extraction appels
        var model = SpoonCallGraphExtractor.buildModel(srcRoot);
        Map<String, Set<String>> edges = SpoonCallGraphExtractor.extractEdges(model);

        // 3) On restreint aux 4 classes du sujet (modifie si tu veux élargir)
        Set<String> allowed = new LinkedHashSet<>(List.of("Shape","Point","Rectangle","Circle"));
        edges = SpoonCallGraphExtractor.filterToClasses(edges, allowed);

        // 4) Couplage (paires non orientées)
        CouplingData cd = SpoonCallGraphExtractor.toClassPairs(edges);

        // 5) Affichage + exports
        System.out.println(">>> TP2 – Couplage entre classes via Spoon");
        System.out.println("Total relations inter-classes = " + cd.total);
        cd.counts.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    double w = cd.total == 0 ? 0.0 : e.getValue() / (double) cd.total;
                    System.out.printf(Locale.ROOT, "  %s  -> count=%d, weight=%.4f%n", e.getKey(), e.getValue(), w);
                });

        // CSV
        StringBuilder csv = new StringBuilder("ClassA,ClassB,Weight\n");
        for (var e : cd.counts.entrySet()) {
            double w = cd.total == 0 ? 0.0 : e.getValue() / (double) cd.total;
            csv.append(e.getKey().a).append(',').append(e.getKey().b).append(',').append(String.format(Locale.ROOT,"%.4f",w)).append('\n');
        }
        Files.writeString(Paths.get("coupling_tp2.csv"), csv.toString());

        // DOT
        StringBuilder dot = new StringBuilder("graph CouplingTP2 {\n  node [shape=box,style=rounded];\n");
        for (String c : allowed) dot.append("  \"").append(c).append("\";\n");
        for (var e : cd.counts.entrySet()) {
            double w = cd.total == 0 ? 0.0 : e.getValue() / (double) cd.total;
            double pen = 1.0 + 9.0 * w;
            dot.append("  \"").append(e.getKey().a).append("\" -- \"").append(e.getKey().b)
                    .append("\" [label=\"").append(String.format(Locale.ROOT,"%.3f",w))
                    .append("\", penwidth=").append(String.format(Locale.ROOT,"%.2f",pen)).append("];\n");
        }
        dot.append("}\n");
        Files.writeString(Paths.get("coupling_tp2.dot"), dot.toString());

        System.out.println("Files written: coupling_tp2.csv, coupling_tp2.dot");
    }

    private static Path detectSrcRoot(String[] args) {
        if (args != null && args.length > 0) return Paths.get(args[0]).toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path s2 = cwd.resolve("src/main/java");
        Path s1 = cwd.resolve("src");
        if (Files.isDirectory(s2)) return s2;
        if (Files.isDirectory(s1)) return s1;
        return cwd;
    }
}

