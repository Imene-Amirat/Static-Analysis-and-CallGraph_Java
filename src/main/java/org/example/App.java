package org.example;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.example.parser.SourceParser;
import org.example.processing.FileExplorer;
import org.example.processing.StatisticsService;
import org.example.visitor.MetricsCollector;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

public class App {
    private final JTextArea output = new JTextArea();
    private StatisticsService.ProjectMetrics pm;
    private Map<String,Integer> methodsPerClass;
    private Map<String,Integer> fieldsPerClass;
    private Map<String,Integer> methodLocAll;
    private Map<String,List<String>> methodsByClass;

    public static void main(String[] args) throws Exception {
        new App().start(3);
    }

    private void start(int thresholdX) throws Exception {
        Path root = Paths.get("src/main/java/org/example/codebase");
        List<Path> files = FileExplorer.listJavaFiles(root);
        List<MetricsCollector.FileMetrics> all = new ArrayList<>();

        methodsPerClass = new HashMap<>();
        fieldsPerClass  = new HashMap<>();
        methodLocAll    = new HashMap<>();
        methodsByClass  = new HashMap<>();

        for (Path f : files) {
            String source = Files.readString(f, StandardCharsets.UTF_8);
            CompilationUnit cu = SourceParser.parseFile(f);
            var fm = MetricsCollector.collect(cu, source, f.toString());
            all.add(fm);

            fm.methodsPerClass.forEach((k,v)-> methodsPerClass.merge(k,v,Integer::sum));
            fm.fieldsPerClass.forEach((k,v)->  fieldsPerClass.merge(k,v,Integer::sum));
            fm.methodLoc.forEach((k,v)->      methodLocAll.put(k,v));
            fm.methodLoc.keySet().forEach(k -> {
                String cls = k.contains("#") ? k.substring(0, k.indexOf('#')) : "<unknown>";
                methodsByClass.computeIfAbsent(cls, __ -> new ArrayList<>()).add(k);
            });
        }

        pm = StatisticsService.aggregate(all, thresholdX);
        SwingUtilities.invokeLater(() -> buildUI(thresholdX));
    }

    private void buildUI(int thresholdX) {
        JFrame frame = new JFrame("Analyse statique — Q1 à Q13");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 720);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(15, 10));
        frame.getContentPane().setBackground(new Color(245, 247, 250));

        // --- Titre ---
        JLabel title = new JLabel("Analyse Statique — TP1 (Q1 à Q13)", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(new Color(40, 80, 140));
        title.setBorder(new EmptyBorder(15, 10, 15, 10));
        frame.add(title, BorderLayout.NORTH);

        // --- Panneau de gauche : boutons Q1..Q13 ---
        JPanel left = new JPanel();
        left.setLayout(new GridLayout(0, 1, 6, 6));
        left.setBorder(new EmptyBorder(15, 15, 15, 10));
        left.setBackground(new Color(235, 239, 245));

        for (int i = 1; i <= 13; i++) {
            JButton btn = new JButton("Q" + i);
            btn.setFocusPainted(false);
            btn.setBackground(new Color(70, 130, 180));
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            int questionNum = i;
            btn.addActionListener(e -> handleQuestion(questionNum, thresholdX));
            left.add(btn);
        }
        frame.add(left, BorderLayout.WEST);

        // --- Zone de résultat ---
        output.setEditable(false);
        output.setFont(new Font("Consolas", Font.PLAIN, 16));
        output.setBackground(Color.WHITE);
        output.setMargin(new Insets(10, 10, 10, 10));
        output.setLineWrap(true);
        output.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(output);
        scroll.setBorder(new TitledBorder(
                new LineBorder(new Color(180, 180, 200), 1, true),
                "Résultats", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14),
                new Color(70, 70, 90)
        ));
        frame.add(scroll, BorderLayout.CENTER);

        // --- Footer résumé rapide ---
        JLabel footer = new JLabel(summary(), SwingConstants.CENTER);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        footer.setForeground(new Color(100, 100, 100));
        footer.setBorder(new EmptyBorder(8, 10, 10, 10));
        frame.add(footer, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void handleQuestion(int q, int thresholdX) {
        output.append("\n\n===== Q" + q + " =====\n");
        switch (q) {
            case 1 -> output.append("Nombre de classes : " + pm.totalClasses);
            case 2 -> output.append("Nombre de lignes de code : " + pm.totalLoc);
            case 3 -> output.append("Nombre total de méthodes : " + pm.totalMethods);
            case 4 -> output.append("Nombre total de packages : " + pm.totalPackages);
            case 5 -> output.append("Moyenne méthodes/classe : " + fmt(pm.avgMethodsPerClass));
            case 6 -> output.append("Moyenne lignes/méthode : " + fmt(pm.avgLocPerMethod));
            case 7 -> output.append("Moyenne attributs/classe : " + fmt(pm.avgFieldsPerClass));
            case 8 -> printList(pm.top10pctClassesByMethods, "Top 10% classes (méthodes)");
            case 9 -> printList(pm.top10pctClassesByFields, "Top 10% classes (attributs)");
            case 10 -> printList(pm.intersectionTop, "Intersection des deux tops");
            case 11 -> {
                String xStr = JOptionPane.showInputDialog(null, "Valeur de X ?", "3");
                int X = parseOrDefault(xStr, 3);
                List<String> res = methodsPerClass.entrySet().stream()
                        .filter(e -> e.getValue() > X)
                        .sorted((a,b)->Integer.compare(b.getValue(), a.getValue()))
                        .map(e -> e.getKey() + " (" + e.getValue() + " méthodes)")
                        .collect(Collectors.toList());
                printList(res, "Classes > " + X + " méthodes");
            }
            case 12 -> {
                String[] classes = methodsByClass.keySet().stream().sorted().toArray(String[]::new);
                String choice = (String) JOptionPane.showInputDialog(null, "Choisir une classe :", "Q12",
                        JOptionPane.PLAIN_MESSAGE, null, classes, classes.length > 0 ? classes[0] : null);
                if (choice != null) {
                    Map<String,Integer> locForClass = methodLocAll.entrySet().stream()
                            .filter(e -> e.getKey().startsWith(choice + "#"))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    Map<String,Integer> top = topPercentMap(locForClass, 10);
                    printMap(top, "Top 10% méthodes (classe " + choice + ")");
                }
            }
            case 13 -> output.append("Max paramètres dans une méthode : " + pm.maxParams);
        }
        output.append("\n");
    }

    // --- Utilitaires ---
    private void printList(List<String> list, String title) {
        output.append(title + " :\n");
        if (list.isEmpty()) output.append("(aucune)\n");
        int i=1;
        for (String s : list) output.append("  %2d. %s\n".formatted(i++, s));
    }

    private void printMap(Map<String,Integer> map, String title) {
        output.append(title + " :\n");
        if (map.isEmpty()) output.append("(vide)\n");
        map.forEach((k,v) -> output.append("  %-40s -> %d LOC\n".formatted(k, v)));
    }

    private static String fmt(double d) { return String.format("%.2f", d); }
    private static int parseOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
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

    private String summary() {
        return "Résumé : " +
                pm.totalFiles + " fichiers | " +
                pm.totalPackages + " packages | " +
                pm.totalClasses + " classes | " +
                pm.totalMethods + " méthodes | " +
                pm.totalFields + " attributs | " +
                pm.totalLoc + " LOC";
    }
}