package org.example.visitor;

import org.eclipse.jdt.core.dom.*;
import java.util.*;

//Compter le nombre de classes dans le fichier
//Enregistrer combien de méthodes et d’attributs chaque classe possède

//Ce visiteur permet de savoir combien de classes, méthodes, et attributs existent, par classe.
public class ClassVisitor extends ASTVisitor {
    public int classCount = 0;
    public final Map<String, Integer> methodsPerClass = new HashMap<>();
    public final Map<String, Integer> fieldsPerClass  = new HashMap<>();

    private String currentClass = null;

    @Override
    public boolean visit(TypeDeclaration node) {
        currentClass = node.getName().getIdentifier();
        classCount++;
        methodsPerClass.putIfAbsent(currentClass, 0);
        fieldsPerClass.putIfAbsent(currentClass, 0);
        return true;
    }

    @Override
    public void endVisit(TypeDeclaration node) {
        currentClass = null;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        if (currentClass != null) {
            methodsPerClass.merge(currentClass, 1, Integer::sum);
        }
        return true;
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        if (currentClass != null) {
            int varCount = node.fragments().size(); // int a,b,c; -> 3
            fieldsPerClass.merge(currentClass, varCount, Integer::sum);
        }
        return true;
    }
}

