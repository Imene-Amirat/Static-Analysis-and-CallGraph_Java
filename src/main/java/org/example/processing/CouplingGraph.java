package org.example.processing;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Couplage(A,B) = (# d'appels inter-classes entre A et B) / (somme de tous les appels inter-classes)
 * Ici on NE CONSIDÈRE que les classes du "whitelist" (Shape, Point, Rectangle, Circle).
 * - Graphe non orienté (A,B) == (B,A)
 * - On ignore tous les appels intra-classe (A -> A)
 * - On ignore toute classe hors whitelist (ex: ext, <external>, Math, etc.)
 */
public class CouplingGraph {

    /** Paire non orientée (ordre canonique) */
    public static final class Pair {
        public final String a, b;
        public Pair(String x, String y) {
            if (x.equals(y)) throw new IllegalArgumentException("Pair must be between two different classes");
            if (x.compareTo(y) <= 0) { this.a = x; this.b = y; } else { this.a = y; this.b = x; }
        }
        @Override public boolean equals(Object o) { return o instanceof Pair p && a.equals(p.a) && b.equals(p.b); }
        @Override public int hashCode() { return Objects.hash(a, b); }
        @Override public String toString() { return a + " -- " + b; }
    }

    /** Comptes bruts (numerateurs) et somme totale (dénominateur) */
    private final Map<Pair, Integer> counts = new LinkedHashMap<>();
    private int totalInterClassCalls = 0;

    /** Toutes les classes retenues (pour dessiner les 4 nœuds même sans arêtes) */
    private final Set<String> classes = new LinkedHashSet<>();

    public Map<Pair,Integer> counts() { return Collections.unmodifiableMap(counts); }
    public int total() { return totalInterClassCalls; }
    public Set<String> classes() { return Collections.unmodifiableSet(classes); }

    /** Calcule le graphe de couplage en ne gardant que allowedClasses. */
    public static CouplingGraph fromCallGraph(CallGraph cg, Set<String> allowedClasses) {
        CouplingGraph g = new CouplingGraph();
        // Assurer que les 4 nœuds existent même si aucun lien
        g.classes.addAll(allowedClasses);

        Map<String, Set<String>> edges = cg.edges;
        if (edges == null) return g;

        for (Map.Entry<String, Set<String>> e : edges.entrySet()) {
            String from = e.getKey();                      // "Class#method"
            String fromClass = classOf(from);
            if (fromClass == null || !allowedClasses.contains(fromClass)) continue;

            for (String to : e.getValue()) {
                String toClass = classOf(to);
                if (toClass == null || !allowedClasses.contains(toClass)) continue;
                if (fromClass.equals(toClass)) continue; // uniquement inter-classes

                Pair p = new Pair(fromClass, toClass);
                g.counts.merge(p, 1, Integer::sum);
                g.totalInterClassCalls++;
            }
        }
        return g;
    }

    /** Poids normalisé Couplage(A,B) dans [0,1]. */
    public double weight(String c1, String c2) {
        if (c1.equals(c2) || totalInterClassCalls == 0) return 0.0;
        Integer v = counts.get(new Pair(c1, c2));
        return v == null ? 0.0 : (v / (double) totalInterClassCalls);
    }

    /** "Class#method" -> "Class" */
    private static String classOf(String classHashMethod) {
        if (classHashMethod == null) return null;
        int i = classHashMethod.indexOf('#');
        return i > 0 ? classHashMethod.substring(0, i) : null;
    }

    /** Export CSV: ClassA,ClassB,Weight (pas de Count, comme demandé) */
    public String toCsvWeightsOnly() {
        StringBuilder sb = new StringBuilder("ClassA,ClassB,Weight\n");
        // Tri par poids décroissant
        List<Map.Entry<Pair, Integer>> sorted = counts.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());
        for (var e : sorted) {
            double w = totalInterClassCalls == 0 ? 0.0 : (e.getValue() / (double) totalInterClassCalls);
            sb.append(e.getKey().a).append(',')
                    .append(e.getKey().b).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", w))
                    .append('\n');
        }
        return sb.toString();
    }

    /** DOT non orienté avec label = Weight (pas de Count). */
    public String toDotWeightsOnly() {
        StringBuilder sb = new StringBuilder("graph CouplingWeights {\n");
        sb.append("  graph [overlap=false];\n  node [shape=box, style=rounded];\n");

        // Ajouter tous les nœuds même isolés
        for (String c : classes) {
            sb.append("  \"").append(c).append("\";\n");
        }

        for (var e : counts.entrySet()) {
            Pair p = e.getKey();
            double w = totalInterClassCalls == 0 ? 0.0 : (e.getValue() / (double) totalInterClassCalls);
            double pen = 1.0 + 9.0 * w; // épaisseur ∝ poids
            sb.append("  \"").append(p.a).append("\" -- \"").append(p.b).append("\" ")
                    .append("[label=\"").append(String.format(Locale.ROOT, "%.4f", w)).append("\", ")
                    .append("penwidth=").append(String.format(Locale.ROOT, "%.2f", pen))
                    .append("];\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
