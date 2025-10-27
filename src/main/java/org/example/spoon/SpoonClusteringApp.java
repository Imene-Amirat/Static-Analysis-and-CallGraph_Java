package org.example.spoon;

import spoon.reflect.CtModel;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.spoon.SpoonCallGraphExtractor.*;

public class SpoonClusteringApp {

    public static void main(String[] args) throws Exception {
        Path srcRoot = detectSrcRoot(args);
        CtModel model = SpoonCallGraphExtractor.buildModel(srcRoot);
        Map<String, Set<String>> edges = SpoonCallGraphExtractor.extractEdges(model);

        Set<String> allowed = new LinkedHashSet<>(List.of("Shape","Point","Rectangle","Circle"));
        edges = SpoonCallGraphExtractor.filterToClasses(edges, allowed);

        CouplingData cd = SpoonCallGraphExtractor.toClassPairs(edges);

        // --- HAC average-linkage sur la matrice de poids ---
        List<String> classes = new ArrayList<>(allowed);
        double[][] W = buildWeightMatrix(classes, cd);

        Node root = hacAverageLinkage(classes, W);
        System.out.println(">>> TP2 – Dendrogramme (liaison moyenne)");
        printDendrogram(root, 0);

        // --- Modules via CP et max M/2 ---
        double CP = 0.30;                         // modifiable
        int maxModules = Math.max(1, classes.size()/2);

        List<Set<String>> modules = cutByCP(root, W, classes, CP, maxModules);
        System.out.println("\n>>> Modules (CP="+CP+", max="+maxModules+")");
        int i=1;
        for (Set<String> m : modules) {
            double avg = avgInternal(m, W, classes);
            String s = m.stream().sorted().collect(Collectors.joining(", "));
            System.out.printf(Locale.ROOT, "  Module %d: {%s}  avg=%.4f  (pairs=%d)%n", i++, s, avg, pairs(m.size()));
        }
    }

    // ---------- utils ----------
    private static Path detectSrcRoot(String[] args) {
        if (args != null && args.length > 0) return Paths.get(args[0]).toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path s2 = cwd.resolve("src/main/java");
        Path s1 = cwd.resolve("src");
        if (Files.isDirectory(s2)) return s2;
        if (Files.isDirectory(s1)) return s1;
        return cwd;
    }

    private static double[][] buildWeightMatrix(List<String> C, CouplingData cd) {
        int n = C.size();
        double[][] W = new double[n][n];
        for (var e : cd.counts.entrySet()) {
            int i = C.indexOf(e.getKey().a);
            int j = C.indexOf(e.getKey().b);
            double w = cd.total==0 ? 0.0 : e.getValue()/(double)cd.total;
            W[i][j]=W[j][i]=w;
        }
        return W;
    }

    // HAC average-linkage compact
    private static Node hacAverageLinkage(List<String> C, double[][] W) {
        Map<Integer, Set<Integer>> clusters = new LinkedHashMap<>();
        Map<Integer, Node> nodes = new LinkedHashMap<>();
        int next=0, n=C.size();
        for (int i=0;i<n;i++) { clusters.put(next, new LinkedHashSet<>(List.of(i))); nodes.put(next, new Node(Set.of(C.get(i)),null,null,0)); next++; }

        while (clusters.size()>1) {
            double best=-1; int A=-1,B=-1;
            List<Integer> ids = new ArrayList<>(clusters.keySet());
            for (int i=0;i<ids.size();i++) for (int j=i+1;j<ids.size();j++) {
                int a=ids.get(i), b=ids.get(j);
                double s = avgBetween(clusters.get(a), clusters.get(b), W);
                if (s>best){best=s;A=a;B=b;}
            }
            Set<Integer> merged=new LinkedHashSet<>(clusters.get(A)); merged.addAll(clusters.get(B));
            Set<String> names = new LinkedHashSet<>(); for(int k:merged) names.add(C.get(k));
            Node parent = new Node(names, nodes.get(A), nodes.get(B), best);
            clusters.remove(A); clusters.remove(B); nodes.remove(A); nodes.remove(B);
            clusters.put(next, merged); nodes.put(next, parent); next++;
        }
        return nodes.values().iterator().next();
    }

    private static double avgBetween(Set<Integer> A, Set<Integer> B, double[][] W){
        double s=0; int c=0; for(int i:A) for(int j:B){ s+=W[i][j]; c++; }
        return c==0?0:s/c;
    }

    private static void printDendrogram(Node n, int depth){
        if(n==null) return;
        if(n.left==null && n.right==null){
            System.out.printf("%s- %s%n","  ".repeat(depth), n.classes.iterator().next()); return;
        }
        printDendrogram(n.left, depth+1);
        printDendrogram(n.right, depth+1);
        System.out.printf("%s[merge sim=%.3f] %s%n","  ".repeat(depth), n.similarity, n.classes);
    }

    // Modules: découpe récursive par CP, limitée à maxModules
    private static List<Set<String>> cutByCP(Node root, double[][] W, List<String> C, double CP, int max) {
        List<Set<String>> out=new ArrayList<>();
        cutRec(root,W,C,CP,max,out);
        while(out.size()>max){ // fusion simple si dépassement
            int iMin=0,jMin=1; int best=Integer.MAX_VALUE;
            for(int i=0;i<out.size();i++) for(int j=i+1;j<out.size();j++){
                int sz=out.get(i).size()+out.get(j).size(); if(sz<best){best=sz;iMin=i;jMin=j;}
            }
            Set<String> m=new LinkedHashSet<>(out.get(iMin)); m.addAll(out.get(jMin));
            out.remove(jMin); out.remove(iMin); out.add(m);
        }
        return out;
    }
    private static void cutRec(Node n, double[][] W, List<String> C, double CP, int max, List<Set<String>> acc){
        double avg = avgInternal(n.classes,W,C);
        if(avg>=CP || n.left==null || n.right==null || acc.size()+2>max){
            acc.add(n.classes); return;
        }
        cutRec(n.left, W, C, CP, max, acc);
        cutRec(n.right, W, C, CP, max, acc);
    }

    private static double avgInternal(Set<String> classes, double[][] W, List<String> C){
        if(classes.size()<2) return 0.0;
        List<Integer> ids = classes.stream().map(C::indexOf).collect(Collectors.toList());
        double s=0; int c=0; for(int i=0;i<ids.size();i++) for(int j=i+1;j<ids.size();j++){ s+=W[ids.get(i)][ids.get(j)]; c++; }
        return c==0?0:s/c;
    }
    private static int pairs(int n){ return n<2?0:n*(n-1)/2; }

    // Dendrogram node
    private static final class Node {
        final Set<String> classes; final Node left,right; final double similarity;
        Node(Set<String> c, Node l, Node r, double s){ classes=c; left=l; right=r; similarity=s; }
    }
}

