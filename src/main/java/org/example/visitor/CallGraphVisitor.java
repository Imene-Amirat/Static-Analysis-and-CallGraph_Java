package org.example.visitor;

import org.eclipse.jdt.core.dom.*;
import java.util.*;

public class CallGraphVisitor extends ASTVisitor {
    private String currentClass = null;
    private String currentMethod = null;

    // varName -> TypeName  (ex: "center" -> "Point")
    private final Map<String,String> fieldTypes = new HashMap<>();

    // edges: "Class#method" -> set("TargetClass#targetMethod")
    public final Map<String,Set<String>> edges = new HashMap<>();

    private String key(String cls, String mth) { return cls + "#" + mth; }

    @Override public boolean visit(TypeDeclaration node) {
        currentClass = node.getName().getIdentifier();
        fieldTypes.clear();
        return true;
    }
    @Override public void endVisit(TypeDeclaration node) {
        currentClass = null;
        fieldTypes.clear();
    }

    @Override public boolean visit(FieldDeclaration node) {
        String type = node.getType().toString(); // ex: Point
        @SuppressWarnings("unchecked")
        List<VariableDeclarationFragment> frags = node.fragments();
        for (var f : frags) fieldTypes.put(f.getName().getIdentifier(), type);
        return true;
    }

    @Override public boolean visit(MethodDeclaration node) {
        currentMethod = node.getName().getIdentifier();
        edges.computeIfAbsent(key(currentClass, currentMethod), __ -> new LinkedHashSet<>());
        return true;
    }
    @Override public void endVisit(MethodDeclaration node) { currentMethod = null; }

    @Override public boolean visit(MethodInvocation node) {
        if (currentClass == null || currentMethod == null) return true;

        String targetClass = resolveTargetClass(node.getExpression());
        String targetMethod = node.getName().getIdentifier();
        edges.computeIfAbsent(key(currentClass, currentMethod), __ -> new LinkedHashSet<>())
                .add(key(targetClass, targetMethod));
        return true;
    }

    @Override public boolean visit(SuperMethodInvocation node) {
        if (currentClass == null || currentMethod == null) return true;
        String targetMethod = node.getName().getIdentifier();
        edges.computeIfAbsent(key(currentClass, currentMethod), __ -> new LinkedHashSet<>())
                .add(key(currentClass, "super."+targetMethod));
        return true;
    }

    private String resolveTargetClass(Expression expr) {
        if (expr == null) return currentClass;                // appel interne
        if (expr instanceof ThisExpression) return currentClass;
        if (expr instanceof SimpleName sn) {
            String name = sn.getIdentifier();                 // ex: center
            return fieldTypes.getOrDefault(name, "<external>");
        }
        if (expr instanceof FieldAccess fa) {
            String name = fa.getName().getIdentifier();       // ex: this.center  (-> "center")
            return fieldTypes.getOrDefault(name, "<external>");
        }
        if (expr instanceof QualifiedName qn) {
            String left = qn.getQualifier().getFullyQualifiedName(); // ex: obj.m (-> "obj")
            return fieldTypes.getOrDefault(left, "<external>");
        }
        return "<external>";
    }
}

