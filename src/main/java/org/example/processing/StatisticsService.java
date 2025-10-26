package org.example.processing;

import org.example.visitor.MetricsCollector.FileMetrics;
import java.util.*;
import java.util.stream.*;

/**
 * Cette classe contient les calculs statistiques sur les métriques collectées
 * par la classe MetricsCollector (nombre de classes, méthodes, attributs, etc.).
 */
public class StatisticsService {
    public static class ProjectMetrics {
        public int totalFiles;
        public int totalPackages;
        public int totalClasses;
        public int totalMethods;
        public int totalFields;
        public int totalLoc;                    // LOC totales de l’application
        public double avgMethodsPerClass;
        public double avgFieldsPerClass;
        public double avgLocPerMethod;
        public int maxParams;

        public List<String> top10pctClassesByMethods = new ArrayList<>();
        public List<String> top10pctClassesByFields  = new ArrayList<>();
        public List<String> intersectionTop          = new ArrayList<>();
        public Map<String,Integer> longestMethods    = new LinkedHashMap<>(); // global
        public Map<String,LinkedHashMap<String,Integer>> longestMethodsPerClass = new LinkedHashMap<>();
        public List<String> classesWithMoreThanXMethods = new ArrayList<>();
    }

    public static ProjectMetrics aggregate(List<FileMetrics> files, int thresholdX) {
        ProjectMetrics pm = new ProjectMetrics();
        pm.totalFiles = files.size();

        // packages déclarés distincts via PackageVisitor
        pm.totalPackages = (int) files.stream()
                .map(f -> f.packageName == null ? "" : f.packageName)
                .filter(s -> !s.isBlank())
                .distinct()
                .count();

        pm.totalClasses = files.stream().mapToInt(f -> f.classes).sum();
        pm.totalMethods = files.stream().mapToInt(f -> f.methods).sum();
        pm.totalFields  = files.stream().mapToInt(f -> f.fields).sum();
        pm.totalLoc     = files.stream().mapToInt(f -> f.fileLoc).sum();
        pm.maxParams    = files.stream().mapToInt(f -> f.maxParamsInFile).max().orElse(0);

        // Fusion méthodes/attributs par classe + LOC par méthode
        Map<String,Integer> methodsPerClass = new HashMap<>();
        Map<String,Integer> fieldsPerClass  = new HashMap<>();
        Map<String,Integer> methodLocAll    = new HashMap<>();
        Map<String,Map<String,Integer>> methodLocByClass = new HashMap<>();

        for (var fm : files) {
            fm.methodsPerClass.forEach((k,v) -> methodsPerClass.merge(k, v, Integer::sum));
            fm.fieldsPerClass.forEach((k,v)  -> fieldsPerClass.merge(k, v, Integer::sum));
            fm.methodLoc.forEach((k,v) -> {
                methodLocAll.put(k, v);
                String cls = k.contains("#") ? k.substring(0, k.indexOf('#')) : "<unknown>";
                methodLocByClass.computeIfAbsent(cls, __ -> new HashMap<>()).put(k, v);
            });
        }

        pm.avgMethodsPerClass = methodsPerClass.isEmpty()
                ? 0 : methodsPerClass.values().stream().mapToInt(i->i).average().orElse(0);
        pm.avgFieldsPerClass  = fieldsPerClass.isEmpty()
                ? 0 : fieldsPerClass.values().stream().mapToInt(i->i).average().orElse(0);
        pm.avgLocPerMethod    = methodLocAll.isEmpty()
                ? 0 : methodLocAll.values().stream().mapToInt(i->i).average().orElse(0);

        // Top 10% classes (méthodes / attributs)
        pm.top10pctClassesByMethods = topPercent(methodsPerClass, 10);
        pm.top10pctClassesByFields  = topPercent(fieldsPerClass, 10);

        // Intersection
        Set<String> inter = new HashSet<>(pm.top10pctClassesByMethods);
        inter.retainAll(pm.top10pctClassesByFields);
        pm.intersectionTop = new ArrayList<>(inter);

        // Top 10% méthodes par LOC (global)
        pm.longestMethods = topPercentMap(methodLocAll, 10);

        // Top 10% méthodes par LOC **par classe**
        for (var e : methodLocByClass.entrySet()) {
            String cls = e.getKey();
            Map<String,Integer> m = e.getValue();
            pm.longestMethodsPerClass.put(cls, topPercentMap(m, 10));
        }

        // Classes avec plus de X méthodes
        pm.classesWithMoreThanXMethods = methodsPerClass.entrySet().stream()
                .filter(en -> en.getValue() > thresholdX)
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return pm;
    }

    private static List<String> topPercent(Map<String,Integer> map, int pct) {
        if (map.isEmpty()) return List.of();
        int n = Math.max(1, (int)Math.ceil(map.size() * (pct/100.0)));
        return map.entrySet().stream()
                .sorted((e1,e2)->Integer.compare(e2.getValue(), e1.getValue()))
                .limit(n)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static LinkedHashMap<String,Integer> topPercentMap(Map<String,Integer> map, int pct) {
        if (map.isEmpty()) return new LinkedHashMap<>();
        int n = Math.max(1, (int)Math.ceil(map.size() * (pct/100.0)));
        return map.entrySet().stream()
                .sorted((e1,e2)->Integer.compare(e2.getValue(), e1.getValue()))
                .limit(n)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a,b)->a, LinkedHashMap::new
                ));
    }
}

