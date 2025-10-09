package org.example.visitor;

import org.eclipse.jdt.core.dom.*;

public class PackageVisitor extends ASTVisitor {
    public String packageName = "";

    @Override
    public boolean visit(PackageDeclaration node) {
        packageName = node.getName().getFullyQualifiedName(); // ex: org.example.analyse.input
        return false; // inutile de visiter plus bas
    }
}
