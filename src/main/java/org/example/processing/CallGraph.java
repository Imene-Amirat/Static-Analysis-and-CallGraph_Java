package org.example.processing;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.example.visitor.CallGraphVisitor;

import java.util.*;

public class CallGraph {
    // Map représentant les arêtes du graphe : clé = méthode appelante, valeur = méthodes appelées
    public final Map<String, Set<String>> edges = new LinkedHashMap<>();

    public void mergeFrom(CompilationUnit cu) {
        // Création d’un visiteur pour extraire les appels de méthode
        CallGraphVisitor v = new CallGraphVisitor();
        cu.accept(v);
        v.edges.forEach((k,vs) ->
                edges.computeIfAbsent(k, __ -> new LinkedHashSet<>()).addAll(vs)
        );
    }

    public Set<String> nodes() {
        // Ensemble contenant tous les nœuds (appelants + appelés)
        Set<String> s = new LinkedHashSet<>(edges.keySet());
        for (var vs : edges.values()) s.addAll(vs);
        return s;
    }
}

