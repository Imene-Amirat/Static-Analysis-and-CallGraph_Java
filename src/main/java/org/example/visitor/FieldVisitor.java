package org.example.visitor;

import org.eclipse.jdt.core.dom.*;

//Compter le nombre total d’attributs (fields) dans le fichier Java,
public class FieldVisitor extends ASTVisitor {
    public int totalFields = 0;

    @Override
    public boolean visit(FieldDeclaration node) {
        totalFields += node.fragments().size();
        return true;
    }
}

