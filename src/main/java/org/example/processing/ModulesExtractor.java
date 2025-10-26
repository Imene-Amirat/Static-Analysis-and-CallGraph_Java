package org.example.processing;

import java.util.*;

/**
 * Extrait des modules à partir du dendrogramme en respectant :
 *  - <= M/2 modules
 *  - chaque module = classes d'une branche (découpe du dendrogramme)
 *  - moyenne interne du module > CP
 */
public final class ModulesExtractor {

    public static final class Module {
        public final Set<String> classes;
        public final double avgCoupling;
        public Module(Set<String> classes, double avg) {
            this.classes = Collections.unmodifiableSet(new LinkedHashSet<>(classes));
            this.avgCoupling = avg;
        }
        @Override public String toString() {
            return "Module" + classes + "  avg=" + String.format(java.util.Locale.ROOT,"%.4f", avgCoupling);
        }
    }

    /**
     * Découpe le dendrogramme en modules.
     * Stratégie : DFS — si le cluster courant n'atteint pas CP et qu'on peut couper (respect maxModules),
     * on tente de descendre (left/right) ; sinon on prend le cluster tel quel (même s'il est < CP si bloqué par max).
     */
    public static List<Module> cutIntoModules(HierarchicalClustering.Node root,
                                              HierarchicalClustering hc,
                                              double CP,
                                              int maxModules) {
        List<Module> out = new ArrayList<>();
        cutRec(root, hc, CP, maxModules, out);
        // Si on dépasse la limite, on remonte en fusionnant les modules les plus proches (rare ici)
        while (out.size() > maxModules) {
            // fusion naïve : joindre les deux plus petits (en nb de classes)
            int iMin = -1, jMin = -1, best = Integer.MAX_VALUE;
            for (int i=0;i<out.size();i++) for (int j=i+1;j<out.size();j++) {
                int sz = out.get(i).classes.size() + out.get(j).classes.size();
                if (sz < best) { best = sz; iMin=i; jMin=j; }
            }
            Set<String> merge = new LinkedHashSet<>(out.get(iMin).classes);
            merge.addAll(out.get(jMin).classes);
            double avg = hc.averageInternalCoupling(merge);
            out.remove(jMin); out.remove(iMin);
            out.add(new Module(merge, avg));
        }
        return out;
    }

    private static void cutRec(HierarchicalClustering.Node node,
                               HierarchicalClustering hc,
                               double CP,
                               int maxModules,
                               List<Module> acc) {
        if (node == null) return;
        double avg = hc.averageInternalCoupling(node.classes);
        if ((avg >= CP) || node.isLeaf()) {
            // On prend ce cluster comme module (respecte "une seule branche")
            acc.add(new Module(node.classes, avg));
            return;
        }
        // Si on peut encore ajouter au moins 2 modules, on coupe
        if (acc.size() + 2 <= maxModules && node.left != null && node.right != null) {
            cutRec(node.left, hc, CP, maxModules, acc);
            cutRec(node.right, hc, CP, maxModules, acc);
        } else {
            // sinon, on prend tel quel (contrainte M/2 prioritaire)
            acc.add(new Module(node.classes, avg));
        }
    }
}
