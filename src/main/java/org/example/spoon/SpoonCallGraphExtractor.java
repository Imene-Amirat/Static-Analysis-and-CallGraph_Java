package org.example.spoon;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import java.nio.file.Path;
import java.util.*;

/**
 * Construit un graphe d'appel (méthode -> méthode) avec Spoon.
 * Noeud = "Class#method", arête = invocation.
 */
public class SpoonCallGraphExtractor {

    /** Construit le modèle Spoon à partir du dossier sources. */
    public static CtModel buildModel(Path srcRoot) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(srcRoot.toString());
        launcher.getEnvironment().setNoClasspath(true); // tolérant aux libs absentes
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.buildModel();
        return launcher.getModel();
    }

    /**
     * Extrait les arêtes d'appel inter-méthodes.
     * @return Map sourceNode -> Set of targetNodes (ex. Shape#area -> {Point#distanceTo})
     */
    public static Map<String, Set<String>> extractEdges(CtModel model) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();

        // ✅ Use TypeFilter to get a typed list of CtInvocation<?>
        for (CtInvocation<?> inv : model.getElements(new TypeFilter<>(CtInvocation.class))) {

            // caller (enclosing executable)
            CtExecutable<?> enclosing = inv.getParent(CtExecutable.class);
            if (!(enclosing instanceof CtMethod<?>)) continue;

            CtMethod<?> callerMethod = (CtMethod<?>) enclosing;
            CtType<?> callerType = callerMethod.getDeclaringType();
            if (callerType == null) continue;

            String caller = callerType.getSimpleName() + "#" + callerMethod.getSimpleName();

            // callee (executable reference, if resolvable)
            CtExecutableReference<?> calleeRef = inv.getExecutable();
            if (calleeRef == null) continue;

            CtTypeReference<?> declTypeRef = calleeRef.getDeclaringType();
            String calleeOwner = (declTypeRef != null) ? declTypeRef.getSimpleName() : null;
            String calleeName  = calleeRef.getSimpleName();

            if (calleeOwner == null || calleeName == null) continue;
            String callee = calleeOwner + "#" + calleeName;

            edges.computeIfAbsent(caller, k -> new LinkedHashSet<>()).add(callee);
        }
        return edges;
    }


    /** Utilitaire pour limiter aux classes "autorisées" (ex: Shape, Point, Rectangle, Circle) et virer les externes. */
    public static Map<String, Set<String>> filterToClasses(Map<String, Set<String>> edges, Set<String> allowedClasses) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (var e : edges.entrySet()) {
            String callerClass = e.getKey().substring(0, e.getKey().indexOf('#'));
            if (!allowedClasses.contains(callerClass)) continue;
            for (String callee : e.getValue()) {
                String calleeClass = callee.substring(0, callee.indexOf('#'));
                if (!allowedClasses.contains(calleeClass)) continue;
                if (callerClass.equals(calleeClass)) continue; // inter-classes uniquement
                out.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>()).add(callee);
            }
        }
        return out;
    }

    /** Agrège les arêtes en paires de classes (non orientées) et renvoie (pair -> count, total). */
    public static CouplingData toClassPairs(Map<String, Set<String>> edges) {
        Map<Pair, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (var e : edges.entrySet()) {
            String aClass = e.getKey().substring(0, e.getKey().indexOf('#'));
            for (String tgt : e.getValue()) {
                String bClass = tgt.substring(0, tgt.indexOf('#'));
                if (aClass.equals(bClass)) continue;
                Pair p = new Pair(aClass, bClass);
                counts.merge(p, 1, Integer::sum);
                total++;
            }
        }
        return new CouplingData(counts, total);
    }

    /** Paire non orientée. */
    public static final class Pair {
        public final String a, b;
        public Pair(String x, String y) {
            if (x.compareTo(y) <= 0) { a = x; b = y; } else { a = y; b = x; }
        }
        @Override public boolean equals(Object o) { return o instanceof Pair p && a.equals(p.a) && b.equals(p.b); }
        @Override public int hashCode() { return Objects.hash(a, b); }
        @Override public String toString() { return a + " -- " + b; }
    }

    /** Données de couplage (comptes + total). */
    public static final class CouplingData {
        public final Map<Pair, Integer> counts;
        public final int total;
        public CouplingData(Map<Pair, Integer> counts, int total) {
            this.counts = counts; this.total = total;
        }
    }
}

