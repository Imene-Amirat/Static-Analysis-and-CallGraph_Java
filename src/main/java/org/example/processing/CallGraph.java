package org.example.processing;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.example.visitor.CallGraphVisitor;

import java.util.*;

public class CallGraph {
    public final Map<String, Set<String>> edges = new LinkedHashMap<>();

    public void mergeFrom(CompilationUnit cu) {
        CallGraphVisitor v = new CallGraphVisitor();
        cu.accept(v);
        v.edges.forEach((k,vs) ->
                edges.computeIfAbsent(k, __ -> new LinkedHashSet<>()).addAll(vs)
        );
    }

    public Set<String> nodes() {
        Set<String> s = new LinkedHashSet<>(edges.keySet());
        for (var vs : edges.values()) s.addAll(vs);
        return s;
    }
}

