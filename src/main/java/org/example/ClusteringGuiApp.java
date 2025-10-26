package org.example;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.example.parser.SourceParser;
import org.example.processing.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/** UI Swing : onglet Dendrogramme + onglet Modules (avec CP). */
public class ClusteringGuiApp {

    public static void main(String[] args) throws Exception {
        // 0) Détection SRC
        Path srcRoot;
        if (args.length == 0) {
            Path cwd = Paths.get("").toAbsolutePath().normalize();
            Path s2 = cwd.resolve("src/main/java");
            Path s1 = cwd.resolve("src");
            srcRoot = Files.isDirectory(s2) ? s2 : (Files.isDirectory(s1) ? s1 : cwd);
        } else {
            srcRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        }

        // 1) Construit CallGraph
        var files = FileExplorer.listJavaFiles(srcRoot);
        CallGraph callGraph = new CallGraph();
        for (Path f : files) {
            try {
                CompilationUnit cu = SourceParser.parseFile(f);
                callGraph.mergeFrom(cu);
            } catch (Exception ex) {
                System.err.println("WARN parse: " + f + " : " + ex.getMessage());
            }
        }

        // 2) CouplingGraph filtré aux classes internes (tu peux restreindre si besoin)
        Set<String> allowed = new LinkedHashSet<>(List.of("Shape","Point","Rectangle","Circle"));
        CouplingGraph cg = CouplingGraph.fromCallGraph(callGraph, allowed);

        // 3) Clustering hiérarchique
        HierarchicalClustering hac = new HierarchicalClustering(allowed, cg);
        HierarchicalClustering.Node root = hac.cluster();

        // 4) Lancer UI
        SwingUtilities.invokeLater(() -> showUI(hac, root));
    }

    private static void showUI(HierarchicalClustering hac, HierarchicalClustering.Node root) {
        JFrame f = new JFrame("Clustering hiérarchique — Modules par couplage");
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setSize(1200, 780);

        // Onglet 1 : Dendrogramme
        DendrogramPanel dendro = new DendrogramPanel(root);

        // Onglet 2 : Modules
        JPanel modulesPanel = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField cpField = new JTextField("0.30", 6);
        JButton run = new JButton("Identifier les modules");
        JLabel info = new JLabel(" Règles: ≤ M/2 modules • moyenne interne > CP • modules = branches du dendrogramme");
        top.add(new JLabel("CP = ")); top.add(cpField); top.add(run); top.add(info);

        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        modulesPanel.add(top, BorderLayout.NORTH);
        modulesPanel.add(new JScrollPane(list), BorderLayout.CENTER);

        run.addActionListener(_ -> {
            double CP;
            try { CP = Double.parseDouble(cpField.getText().trim()); }
            catch (Exception ex) { JOptionPane.showMessageDialog(f, "CP invalide"); return; }
            int M = root.classes.size();
            int maxModules = Math.max(1, M / 2);

            List<ModulesExtractor.Module> modules =
                    ModulesExtractor.cutIntoModules(root, hac, CP, maxModules);

            model.clear();
            model.addElement(String.format("M = %d, max modules = %d, CP = %.3f", M, maxModules, CP));
            model.addElement("--------------------------------------------------------------");
            for (int i=0;i<modules.size();i++) {
                var m = modules.get(i);
                String classes = m.classes.stream().sorted().collect(Collectors.joining(", "));
                model.addElement(String.format("Module %d: {%s}  avg=%.4f  (pairs=%d)",
                        i+1, classes, m.avgCoupling, pairsCount(m.classes.size())));
            }
        });

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dendrogramme", dendro);
        tabs.addTab("Modules (CP)", modulesPanel);

        f.setContentPane(tabs);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        
    }

    private static int pairsCount(int n) { return n < 2 ? 0 : (n*(n-1))/2; }

    // ======= DESSIN DENDROGRAMME SIMPLE =======
    // ======= DENDROGRAM: redesigned, horizontal with scale & hover =======
    // ======= DENDROGRAM v2 — vertical, clean, readable =======
    static class DendrogramPanel extends JPanel {
        private final HierarchicalClustering.Node root;

        // layout & style
        private static final int PAD_L = 90, PAD_R = 40, PAD_T = 70, PAD_B = 60;
        private static final int LEAF_W = 120, LEAF_H = 30, ROW_GAP = 32;
        private static final Color BG = new Color(248,250,253);
        private static final Color GRID = new Color(226,229,236);
        private static final Color EDGE = new Color(130,136,155);
        private static final Color EDGE_HI = new Color(55,118,168);
        private static final Color BOX_FILL = new Color(228,233,242);
        private static final Color BOX_STROKE = new Color(100,108,129);
        private static final Color TEXT = new Color(52,60,79);

        // caches
        private final Map<HierarchicalClustering.Node, Rectangle> leafBox = new HashMap<>();
        private final Map<HierarchicalClustering.Node, Point2D.Double> mergePt = new HashMap<>();
        private List<String> leavesOrder;
        private HierarchicalClustering.Node hover = null;

        DendrogramPanel(HierarchicalClustering.Node root) {
            this.root = root;
            setBackground(BG);
            setOpaque(true);

            // hover
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                    HierarchicalClustering.Node n = pickMerge(e.getPoint());
                    if (n != hover) { hover = n; repaint(); }
                    setToolTipText(n == null ? null :
                            "merge similarity = " + String.format(java.util.Locale.ROOT,"%.3f", clamp(n.similarity))
                                    + " • classes: " + n.classes);
                }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // reset caches
            leafBox.clear(); mergePt.clear();
            leavesOrder = collectLeaves(root);
            if (leavesOrder.isEmpty()) { g2.dispose(); return; }

            int W = getWidth(), H = getHeight();

            // scale axis on the LEFT (0 at top, 1 at bottom)
            int axisY0 = PAD_T;              // sim = 0
            int axisY1 = H - PAD_B;          // sim = 1
            int axisX  = PAD_L - 40;

            // place leaves (top row)
            int nLeaves = leavesOrder.size();
            int totalWidth = Math.max(LEAF_W, (nLeaves-1)*LEAF_W/2 + LEAF_W); // adaptive
            int left = PAD_L + (W - PAD_L - PAD_R - totalWidth)/2;
            int yLeaves = PAD_T;

            for (int i=0;i<nLeaves;i++) {
                int x = left + i*(LEAF_W + 14); // generous x-gap
                Rectangle r = new Rectangle(x, yLeaves, LEAF_W, LEAF_H);
                leafBox.put(findLeafNode(root, leavesOrder.get(i)), r);
            }

            // compute merges (y increases with similarity)
            layoutMerges(root, axisY0, axisY1);

            // grid & axis
            drawGridAndAxis(g2, axisX, axisY0, axisY1, PAD_L, W - PAD_R);

            // edges - normal
            drawEdges(g2, root, false);
            // edges - highlighted
            drawEdges(g2, root, true);

            // draw leaves on top
            for (var e : leafBox.entrySet()) drawLeaf(g2, e.getValue(), oneClass(e.getKey()));

            g2.dispose();
        }

        // ---------- layout ----------
        private void layoutMerges(HierarchicalClustering.Node n, int y0, int y1) {
            if (n.isLeaf()) return;
            layoutMerges(n.left, y0, y1);
            layoutMerges(n.right, y0, y1);

            Rectangle a = bbox(n.left), b = bbox(n.right);
            int xa = centerX(a), xb = centerX(b);

            double sim = clamp(n.similarity);
            double y = y0 + sim * (y1 - y0);   // similarity -> vertical coordinate
            mergePt.put(n, new Point2D.Double((xa+xb)/2.0, y));
        }

        private Rectangle bbox(HierarchicalClustering.Node n) {
            if (n.isLeaf()) return leafBox.get(n);
            Point2D pa = pointOf(n.left), pb = pointOf(n.right);
            int x = (int)Math.min(pa.getX(), pb.getX());
            int y = (int)Math.min(pa.getY(), pb.getY());
            int w = (int)Math.abs(pa.getX()-pb.getX());
            int h = (int)Math.abs(pa.getY()-pb.getY());
            return new Rectangle(x, y, Math.max(w, 1), Math.max(h, 1));
        }

        private Point2D pointOf(HierarchicalClustering.Node n) {
            if (n.isLeaf()) {
                Rectangle r = leafBox.get(n);
                return new Point2D.Double(centerX(r), r.y + r.height); // from bottom center of leaf
            }
            return mergePt.get(n);
        }

        // ---------- drawing ----------
        private void drawGridAndAxis(Graphics2D g2, int axisX, int y0, int y1, int xMin, int xMax) {
            // horizontal grid lines for 0.0 .. 1.0 each 0.1
            g2.setFont(g2.getFont().deriveFont(11f));
            for (int i=0;i<=10;i++) {
                double t = i/10.0;
                int y = (int)Math.round(y0 + t*(y1-y0));
                g2.setColor(GRID);
                g2.drawLine(xMin, y, xMax, y);
                // tick & label at left
                g2.setColor(new Color(120,125,140));
                g2.drawLine(axisX+26, y, axisX+32, y);
                String s = String.format(java.util.Locale.ROOT, "%.1f", t);
                int w = g2.getFontMetrics().stringWidth(s);
                g2.drawString(s, axisX+26 - w - 6, y + 4);
            }
            // axis title
            g2.drawString("Similarity (0..1)", axisX+6, y0 - 12);
        }

        private void drawEdges(Graphics2D g2, HierarchicalClustering.Node n, boolean highlightPass) {
            if (n.isLeaf()) return;

            boolean isHi = (n == hover) || isAncestorOf(hover, n);
            if (highlightPass != isHi) {
                drawEdges(g2, n.left, highlightPass);
                drawEdges(g2, n.right, highlightPass);
                return;
            }

            drawEdges(g2, n.left, highlightPass);
            drawEdges(g2, n.right, highlightPass);

            Point2D pm = mergePt.get(n);
            Point2D pa = pointOf(n.left), pb = pointOf(n.right);

            // style
            Stroke st = new BasicStroke(isHi ? 3.0f : 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            g2.setStroke(st);
            g2.setColor(isHi ? EDGE_HI : EDGE);

            // stepped lines (vertical from child up to merge Y, then horizontal to merge X)
            g2.draw(new Line2D.Double(pa.getX(), pa.getY(), pa.getX(), pm.getY()));
            g2.draw(new Line2D.Double(pb.getX(), pb.getY(), pb.getX(), pm.getY()));
            g2.draw(new Line2D.Double(pa.getX(), pm.getY(), pm.getX(), pm.getY()));
            g2.draw(new Line2D.Double(pb.getX(), pm.getY(), pm.getX(), pm.getY()));

            // similarity badge on right
            drawBadge(g2, String.format(java.util.Locale.ROOT,"%.3f", clamp(n.similarity)),
                    pm.getX() + 8, pm.getY() - 11, isHi);
        }

        private void drawLeaf(Graphics2D g2, Rectangle r, String text) {
            g2.setColor(BOX_FILL);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 16, 16);
            g2.setColor(BOX_STROKE);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 16, 16);

            g2.setColor(TEXT);
            Font f = g2.getFont().deriveFont(Font.BOLD, 13f);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int tx = r.x + (r.width - fm.stringWidth(text))/2;
            int ty = r.y + (r.height + fm.getAscent() - fm.getDescent())/2;
            g2.drawString(text, tx, ty);
        }

        private void drawBadge(Graphics2D g2, String text, double x, double y, boolean hi) {
            Font f = g2.getFont().deriveFont(Font.PLAIN, 12f);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(text) + 10, h = fm.getHeight();
            int bx = (int)Math.round(x), by = (int)Math.round(y - h/2.0);
            RoundRectangle2D rr = new RoundRectangle2D.Double(bx, by, w, h, 12, 12);
            g2.setColor(hi ? new Color(240,247,253) : new Color(250,250,255,220));
            g2.fill(rr);
            g2.setColor(hi ? EDGE_HI : new Color(150,155,170));
            g2.draw(rr);
            g2.setColor(new Color(80,85,100));
            g2.drawString(text, bx + 5, by + fm.getAscent());
        }

        // ---------- helpers ----------
        private List<String> collectLeaves(HierarchicalClustering.Node n) {
            List<String> out = new ArrayList<>(); collectRec(n, out); return out;
        }
        private void collectRec(HierarchicalClustering.Node n, List<String> out) {
            if (n==null) return;
            if (n.isLeaf()) { out.add(oneClass(n)); return; }
            collectRec(n.left, out); collectRec(n.right, out);
        }
        private HierarchicalClustering.Node findLeafNode(HierarchicalClustering.Node n, String name) {
            if (n.isLeaf()) return oneClass(n).equals(name) ? n : null;
            var a = findLeafNode(n.left, name); if (a != null) return a;
            return findLeafNode(n.right, name);
        }
        private boolean isAncestorOf(HierarchicalClustering.Node anc, HierarchicalClustering.Node n) {
            if (anc == null || n == null || n.isLeaf()) return false;
            if (anc == n.left || anc == n.right) return true;
            return isAncestorOf(anc, n.left) || isAncestorOf(anc, n.right);
        }
        private String oneClass(HierarchicalClustering.Node leaf) { return leaf.classes.iterator().next(); }
        private int centerX(Rectangle r) { return r.x + r.width/2; }
        private double clamp(double v) { return Math.max(0, Math.min(1, v)); }

        // pick merge by proximity to merge Y & between child Xs
        private HierarchicalClustering.Node pickMerge(Point p) {
            double tolY = 7.0;
            for (var e : mergePt.entrySet()) {
                HierarchicalClustering.Node n = e.getKey();
                Point2D m = e.getValue();
                Point2D la = pointOf(n.left), lb = pointOf(n.right);
                double xMin = Math.min(la.getX(), lb.getX()), xMax = Math.max(la.getX(), lb.getX());
                if (Math.abs(p.getY() - m.getY()) <= tolY && p.getX() >= xMin-6 && p.getX() <= xMax+6) {
                    return n;
                }
            }
            return null;
        }

        /** Optional: export panel to PNG */
        public void exportPng(java.nio.file.Path out) throws Exception {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(getWidth(), getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            paint(g2);
            g2.dispose();
            javax.imageio.ImageIO.write(img, "png", out.toFile());
        }
    }
}
