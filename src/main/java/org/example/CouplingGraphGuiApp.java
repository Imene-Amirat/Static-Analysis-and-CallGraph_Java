package org.example;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.example.parser.SourceParser;
import org.example.processing.CallGraph;
import org.example.processing.CouplingGraph;
import org.example.processing.FileExplorer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class CouplingGraphGuiApp {

    public static void main(String[] args) throws Exception {
        // 1) SRC root (auto si vide)
        Path srcRoot;
        if (args.length == 0) {
            Path cwd = Paths.get("").toAbsolutePath().normalize();
            Path s2 = cwd.resolve("src/main/java");
            Path s1 = cwd.resolve("src");
            srcRoot = Files.isDirectory(s2) ? s2 : (Files.isDirectory(s1) ? s1 : cwd);
        } else {
            srcRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        }

        // 2) Build call graph (méthode->méthode)
        var javaFiles = FileExplorer.listJavaFiles(srcRoot);
        CallGraph callGraph = new CallGraph();
        for (Path f : javaFiles) {
            try {
                CompilationUnit cu = SourceParser.parseFile(f);
                callGraph.mergeFrom(cu);
            } catch (Exception ex) {
                System.err.println("WARN parse: " + f + " : " + ex.getMessage());
            }
        }

        // 3) Couplage restreint aux 4 classes demandées
        Set<String> allowed = new LinkedHashSet<>(List.of("Shape", "Point", "Rectangle", "Circle"));
        CouplingGraph coupling = CouplingGraph.fromCallGraph(callGraph, allowed);

        // 4) UI
        SwingUtilities.invokeLater(() -> showUI(coupling));
    }

    private static void showUI(CouplingGraph coupling) {
        JFrame frame = new JFrame("Graphe de couplage (poids uniquement) — TP");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1220, 780);

        var panel = new CouplingPanel(coupling);

        // Toolbar
        JToolBar tb = new JToolBar(); tb.setFloatable(false);
        JButton expDot = new JButton("Export DOT (poids)");
        expDot.addActionListener(_ -> {
            try {
                Files.writeString(Paths.get("coupling_weights.dot"), coupling.toDotWeightsOnly());
                JOptionPane.showMessageDialog(frame, "Exporté: coupling_weights.dot");
            } catch (Exception ex) { JOptionPane.showMessageDialog(frame, ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE); }
        });
        JButton expCsv = new JButton("Export CSV (poids)");
        expCsv.addActionListener(_ -> {
            try {
                Files.writeString(Paths.get("coupling_weights.csv"), coupling.toCsvWeightsOnly());
                JOptionPane.showMessageDialog(frame, "Exporté: coupling_weights.csv");
            } catch (Exception ex) { JOptionPane.showMessageDialog(frame, ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE); }
        });

        JLabel legend = new JLabel("  Nœuds = {Shape, Point, Rectangle, Circle} • Arêtes = poids Couplage(A,B) = #A↔B / Total inter-classes • AUCUN count affiché");

        // Status bar
        JLabel status = new JLabel("  Total relations inter-classes (sur ces 4 classes) = " + coupling.total() + "   |   Paires = " + coupling.counts().size());

        tb.add(expDot); tb.add(expCsv); tb.add(Box.createHorizontalStrut(12)); tb.add(legend);

        frame.setLayout(new BorderLayout());
        frame.add(tb, BorderLayout.NORTH);
        frame.add(panel, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ================= UI Panel =================
    static class CouplingPanel extends JPanel {
        private final CouplingGraph cg;
        private final java.util.List<String> classes;
        private final java.util.Map<CouplingGraph.Pair, Integer> counts;
        private final int total;

        private final Map<String, Point2D.Double> pos = new HashMap<>();
        private double zoom = 1.0;
        private Point pan = new Point(0,0);
        private Point lastDrag = null;
        private String hover = null;

        private final int nodeW = 150, nodeH = 42;

        CouplingPanel(CouplingGraph cg) {
            this.cg = cg;
            this.counts = cg.counts();
            this.total = cg.total();

            // On place exactement les 4 nœuds (même si certains n'ont pas d'arêtes)
            Set<String> cls = new LinkedHashSet<>(cg.classes());
            // Forcer l’ordre visuel : Shape | Circle | Point | Rectangle (modifiable)
            this.classes = new ArrayList<>(List.of("Shape", "Circle", "Point", "Rectangle"));
            // si certains manquent (au cas où), on les ajoute
            for (String c : cls) if (!this.classes.contains(c)) this.classes.add(c);

            setBackground(new Color(248,250,253));
            layoutCircle();

            // interactions
            addMouseWheelListener(e -> {
                double old = zoom;
                zoom = e.getPreciseWheelRotation() < 0 ? Math.min(4.0, zoom * 1.1) : Math.max(0.25, zoom / 1.1);
                Point m = e.getPoint();
                pan.x = (int)(m.x - (m.x - pan.x) * (zoom / old));
                pan.y = (int)(m.y - (m.y - pan.y) * (zoom / old));
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { if (SwingUtilities.isRightMouseButton(e)) lastDrag = e.getPoint(); }
                @Override public void mouseReleased(MouseEvent e) { lastDrag = null; }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    if (lastDrag != null) {
                        pan.translate(e.getX() - lastDrag.x, e.getY() - lastDrag.y);
                        lastDrag = e.getPoint();
                        repaint();
                    }
                }
                @Override public void mouseMoved(MouseEvent e) {
                    String hit = pick(toWorld(e.getPoint()));
                    if (!Objects.equals(hit, hover)) { hover = hit; repaint(); }
                }
            });
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { layoutCircle(); repaint(); }
            });
        }

        private void layoutCircle() {
            pos.clear();
            int n = Math.max(1, classes.size());
            double R = Math.max(120, Math.min(getWidth(), getHeight()) * 0.33);
            Point2D.Double c = new Point2D.Double(getWidth()/2.0, getHeight()/2.0);
            for (int i=0; i<n; i++) {
                double ang = 2*Math.PI * i / n;
                double x = c.x + R * Math.cos(ang);
                double y = c.y + R * Math.sin(ang);
                pos.put(classes.get(i), new Point2D.Double(x, y));
            }
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            AffineTransform at = new AffineTransform();
            at.translate(pan.x, pan.y);
            at.scale(zoom, zoom);
            g2.transform(at);

            // 1) Arêtes (label = weight, pas de count)
            for (var e : counts.entrySet()) {
                var p = e.getKey();
                Point2D a = pos.get(p.a), b = pos.get(p.b);
                if (a == null || b == null) continue;

                double w = total == 0 ? 0.0 : (e.getValue() / (double) total);
                float stroke = (float)(1.0 + 9.0 * w);

                g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(143, 149, 170, 165));
                g2.draw(new Line2D.Double(a, b));

                // Label: weight (4 décimales)
                var mid = new Point2D.Double((a.getX()+b.getX())/2.0, (a.getY()+b.getY())/2.0);
                drawLabel(g2, String.format(Locale.ROOT, "%.4f", w), mid.getX(), mid.getY() - 6);
            }

            // 2) Nœuds
            for (String c : classes) {
                Point2D p = pos.get(c);
                if (p == null) continue;
                boolean on = c.equals(hover);
                Shape box = new RoundRectangle2D.Double(p.getX()-nodeW/2.0, p.getY()-nodeH/2.0, nodeW, nodeH, 18, 18);
                g2.setColor(on ? new Color(67,126,168) : new Color(228,233,242));
                g2.fill(box);
                g2.setColor(new Color(102,110,129));
                g2.setStroke(new BasicStroke(1.8f));
                g2.draw(box);
                Font f = g2.getFont().deriveFont(Font.BOLD, 13f);
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (int) (p.getX() - fm.stringWidth(c) / 2.0);
                int ty = (int) (p.getY() + (fm.getAscent() - fm.getDescent()) / 2.0);
                g2.setColor(on ? Color.WHITE : new Color(55,63,82));
                g2.drawString(c, tx, ty);
            }

            // 3) Légende
            g2.setTransform(new AffineTransform()); // reset
            g2.setColor(new Color(120,120,120));
            g2.drawString("Couplage(A,B) = #A↔B / Total inter-classes (uniquement {Shape, Point, Rectangle, Circle}) — Aucune classe externe affichée", 12, getHeight()-14);

            g2.dispose();
        }

        private void drawLabel(Graphics2D g2, String text, double x, double y) {
            Font f = g2.getFont().deriveFont(Font.PLAIN, 12f);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(text) + 8, h = fm.getHeight();
            int bx = (int) (x - w/2.0), by = (int) (y - h/2.0);
            RoundRectangle2D rr = new RoundRectangle2D.Double(bx, by, w, h, 12, 12);
            g2.setColor(new Color(250, 250, 255, 220)); g2.fill(rr);
            g2.setColor(new Color(150, 155, 170)); g2.draw(rr);
            g2.setColor(new Color(80, 85, 100)); g2.drawString(text, bx + 4, by + fm.getAscent());
        }

        private Point toWorld(Point p) {
            try {
                AffineTransform at = new AffineTransform();
                at.translate(pan.x, pan.y); at.scale(zoom, zoom);
                AffineTransform inv = at.createInverse();
                Point2D w = inv.transform(p, null);
                return new Point((int) w.getX(), (int) w.getY());
            } catch (Exception e) { return p; }
        }

        private String pick(Point world) {
            for (String c : classes) {
                Point2D p = pos.get(c);
                if (p == null) continue;
                Rectangle r = new Rectangle((int)(p.getX()-nodeW/2.0), (int)(p.getY()-nodeH/2.0), nodeW, nodeH);
                if (r.contains(world)) return c;
            }
            return null;
        }
    }
}
