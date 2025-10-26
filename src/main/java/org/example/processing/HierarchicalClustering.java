package org.example.processing;

import java.util.*;

/**
 * Clustering hiérarchique agglomératif (average-linkage) basé sur les poids de couplage.
 * A l'étape i, on fusionne les deux clusters ayant la similarité (moyenne des couplages) la plus élevée.
 */
public final class HierarchicalClustering {

    /** Noeud du dendrogramme. */
    public static final class Node {
        public final Set<String> classes; // ensemble des classes dans ce cluster
        public final Node left, right;    // enfants (null si feuille)
        public final double similarity;   // score de fusion (poids moyen inter-clusters) ; 0 pour feuille

        public Node(Set<String> classes, Node left, Node right, double similarity) {
            this.classes = Collections.unmodifiableSet(new LinkedHashSet<>(classes));
            this.left = left;
            this.right = right;
            this.similarity = similarity;
        }

        public boolean isLeaf() { return left == null && right == null; }
    }

    private final Map<String, Integer> idx = new HashMap<>();
    private final List<String> classList;
    private final double[][] w; // matrice w[i][j] = couplage entre classList[i], classList[j]

    public HierarchicalClustering(Set<String> classes, CouplingGraph cg) {
        this.classList = new ArrayList<>(classes);
        for (int i=0;i<classList.size();i++) idx.put(classList.get(i), i);
        int n = classList.size();
        this.w = new double[n][n];
        for (int i=0;i<n;i++) for (int j=0;j<n;j++) {
            if (i==j) w[i][j] = 0.0;
            else w[i][j] = cg.weight(classList.get(i), classList.get(j));
        }
    }

    /** moyenne des poids pour toutes les paires (a,b) avec a∈A, b∈B */
    private double avgSimilarity(Set<Integer> A, Set<Integer> B) {
        if (A.isEmpty() || B.isEmpty()) return 0.0;
        double sum = 0.0; int c = 0;
        for (int i : A) for (int j : B) {
            if (i==j) continue;
            sum += w[i][j];
            c++;
        }
        return c==0 ? 0.0 : sum / c;
    }

    /** Réalise le clustering et renvoie la racine du dendrogramme. */
    public Node cluster() {
        // clusters actifs: id -> indices de classes
        int n = classList.size();
        Map<Integer, Set<Integer>> clusters = new LinkedHashMap<>();
        Map<Integer, Node> nodes = new LinkedHashMap<>();
        int nextId = 0;

        for (int i=0;i<n;i++) {
            Set<Integer> s = new LinkedHashSet<>(List.of(i));
            clusters.put(nextId, s);
            nodes.put(nextId, new Node(Set.of(classList.get(i)), null, null, 0.0));
            nextId++;
        }

        while (clusters.size() > 1) {
            // cherche la meilleure paire (max moyenne)
            double bestSim = -1;
            int bestA = -1, bestB = -1;
            List<Integer> ids = new ArrayList<>(clusters.keySet());
            for (int i=0;i<ids.size();i++) for (int j=i+1;j<ids.size();j++) {
                int a = ids.get(i), b = ids.get(j);
                double sim = avgSimilarity(clusters.get(a), clusters.get(b));
                if (sim > bestSim) { bestSim = sim; bestA = a; bestB = b; }
            }
            // fusion
            Set<Integer> merged = new LinkedHashSet<>(clusters.get(bestA));
            merged.addAll(clusters.get(bestB));
            Set<String> mergedNames = new LinkedHashSet<>();
            for (int k : merged) mergedNames.add(classList.get(k));

            Node left = nodes.get(bestA), right = nodes.get(bestB);
            Node parent = new Node(mergedNames, left, right, bestSim);

            clusters.remove(bestA); clusters.remove(bestB);
            nodes.remove(bestA); nodes.remove(bestB);
            clusters.put(nextId, merged);
            nodes.put(nextId, parent);
            nextId++;
        }

        return nodes.values().iterator().next(); // racine
    }

    /** Moyenne interne de couplage pour un set de classes (toutes paires). */
    public double averageInternalCoupling(Set<String> classes) {
        if (classes.size() <= 1) return 0.0;
        List<Integer> ids = new ArrayList<>();
        for (String c : classes) ids.add(idx.getOrDefault(c, -1));
        double sum = 0; int cnt = 0;
        for (int i=0;i<ids.size();i++) for (int j=i+1;j<ids.size();j++) {
            int a = ids.get(i), b = ids.get(j);
            if (a<0 || b<0) continue;
            sum += w[a][b];
            cnt++;
        }
        return cnt==0 ? 0.0 : sum / cnt;
    }
}
