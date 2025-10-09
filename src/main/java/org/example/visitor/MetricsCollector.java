package org.example.visitor;

import org.eclipse.jdt.core.dom.*;
import java.util.*;

//Centraliser les résultats des trois visiteurs ci-dessuss
public class MetricsCollector {
    public static class FileMetrics {
        public String fileName;
        public String packageName = "";
        public int fileLoc = 0;                         // LOC totales du fichier (non vides)
        public int classes;
        public int methods;
        public int fields;
        public Map<String,Integer> methodsPerClass = new HashMap<>();
        public Map<String,Integer> fieldsPerClass  = new HashMap<>();
        public Map<String,Integer> methodLoc       = new HashMap<>(); // Class#method -> LOC
        public Map<String,Integer> paramsPerMethod = new HashMap<>(); // Class#method -> #params
        public int maxParamsInFile = 0;
    }

    private static int countNonEmptyLoc(String source) {
        int loc = 0;
        for (String line : source.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty()) loc++;
        }
        return loc;
    }

    public static FileMetrics collect(CompilationUnit cu, String source, String fileName) {
        ClassVisitor  cv = new ClassVisitor();
        FieldVisitor  fv = new FieldVisitor();
        MethodVisitor mv = new MethodVisitor(source);
        PackageVisitor pv = new PackageVisitor();

        cu.accept(pv);
        cu.accept(cv);
        cu.accept(fv);
        cu.accept(mv);

        FileMetrics fm = new FileMetrics();
        fm.fileName = fileName;
        fm.packageName = pv.packageName;
        fm.fileLoc = countNonEmptyLoc(source);

        fm.classes  = cv.classCount;
        fm.methods  = mv.totalMethods;
        fm.fields   = fv.totalFields;

        fm.methodsPerClass.putAll(cv.methodsPerClass);
        fm.fieldsPerClass.putAll(cv.fieldsPerClass);
        fm.methodLoc.putAll(mv.methodLoc);
        fm.maxParamsInFile = mv.maxParams;

        // (optionnel) stocker nb params par méthode
        mv.methodLoc.forEach((k, v) -> {}); // rien à faire ici, déjà calculé dans mv
        return fm;
    }
}