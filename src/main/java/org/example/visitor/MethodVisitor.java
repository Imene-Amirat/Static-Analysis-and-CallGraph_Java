package org.example.visitor;

import org.eclipse.jdt.core.dom.*;
import java.util.*;

//Compter le nombre total de méthodes
//→ Trouver la méthode ayant le plus de paramètres
//→ Calculer la taille (nombre de lignes) de chaque méthode
public class MethodVisitor extends ASTVisitor {
    private final String source;
    public int totalMethods = 0;
    public int maxParams = 0;
    public final Map<String, Integer> methodLoc = new HashMap<>(); // key: Class#method

    public MethodVisitor(String source) { this.source = source; }

    @Override
    public boolean visit(MethodDeclaration node) {
        totalMethods++;

        // max paramètres
        int params = node.parameters().size();
        if (params > maxParams) maxParams = params;

        // LOC de la méthode (approx: lignes du segment source)
        int start = node.getStartPosition();
        int len   = node.getLength();
        String snippet = source.substring(start, start + len);
        int loc = (int) snippet.lines().count();

        String owner = (node.getParent() instanceof TypeDeclaration)
                ? ((TypeDeclaration) node.getParent()).getName().getIdentifier()
                : "<unknown>";
        String key = owner + "#" + node.getName().getIdentifier();

        methodLoc.put(key, loc);
        return true;
    }
}

