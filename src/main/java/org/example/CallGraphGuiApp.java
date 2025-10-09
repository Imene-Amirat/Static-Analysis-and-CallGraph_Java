package org.example;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.example.parser.SourceParser;
import org.example.processing.CallGraph;
import org.example.processing.FileExplorer;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class CallGraphGuiApp {

    public static void main(String[] args) throws Exception {
        // Construire le graphe à partir du package input
        Path root = Paths.get("src/main/java/org/example/codebase");
        List<Path> files = FileExplorer.listJavaFiles(root);
        CallGraph cg = new CallGraph();

        for (Path f : files) {
            String source = Files.readString(f, StandardCharsets.UTF_8); // facultatif (non utilisé)
            CompilationUnit cu = SourceParser.parseFile(f);
            cg.mergeFrom(cu);
        }

        SwingUtilities.invokeLater(() -> show(cg));
    }

    private static void show(CallGraph cg) {
        JFrame frame = new JFrame("Graphe d'appel — TP1/TP2");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 750);
        frame.setLocationRelativeTo(null);

        GraphPanel panel = new GraphPanel(cg);
        frame.add(panel, BorderLayout.CENTER);
        frame.add(legend(cg), BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private static JComponent legend(CallGraph cg) {
        JLabel l = new JLabel("Nœuds: Class#method — Cliquez sur un nœud pour surligner ses appels sortants.");
        l.setBorder(BorderFactory.createEmptyBorder(6,10,6,10));
        return l;
    }

    // --------------------- Rendu ---------------------
    static class GraphPanel extends JPanel {
        private final Map<String, Point> pos = new HashMap<>();
        private final List<String> nodes;
        private final Map<String, Set<String>> edges;

        private String focused = null;
        private String draggingNode = null;
        private Point dragOffset = new Point(0,0);

        private double zoom = 1.0;
        private Point pan = new Point(0,0);
        private Point lastPanPoint = null;

        GraphPanel(CallGraph cg) {
            setBackground(new Color(248,250,253));
            this.edges = cg.edges;
            this.nodes = new ArrayList<>(cg.nodes());
            layoutCircle();

            // clic: focus
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    Point wp = toWorld(e.getPoint());
                    String hit = hitTest(wp);
                    if (hit != null) { focused = hit.equals(focused) ? null : hit; repaint(); }
                }
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    Point wp = toWorld(e.getPoint());
                    draggingNode = hitTest(wp);
                    if (draggingNode != null) {
                        Point n = pos.get(draggingNode);
                        dragOffset = new Point(wp.x - n.x, wp.y - n.y);
                    } else {
                        lastPanPoint = e.getPoint(); // début pan
                    }
                }
                @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                    draggingNode = null;
                    lastPanPoint = null;
                }
            });

            // drag: déplacer le nœud ou panner
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (draggingNode != null) {
                        Point wp = toWorld(e.getPoint());
                        pos.put(draggingNode, new Point(wp.x - dragOffset.x, wp.y - dragOffset.y));
                        repaint();
                    } else if (lastPanPoint != null) {
                        Point now = e.getPoint();
                        pan.translate(now.x - lastPanPoint.x, now.y - lastPanPoint.y);
                        lastPanPoint = now;
                        repaint();
                    }
                }
            });

            // molette: zoom
            addMouseWheelListener(e -> {
                double factor = (e.getPreciseWheelRotation() < 0) ? 1.1 : 1/1.1;
                zoom *= factor;
                zoom = Math.max(0.4, Math.min(zoom, 2.5));
                repaint();
            });
        }

        // ---------- Utils pour clipper la ligne sur le bord des rectangles ----------
        // Retourne le point d'intersection entre le segment p->q et le bord du rectangle r.
        // Si pas d'intersection (cas dégénéré), renvoie q.
        private Point intersectLineWithRect(Point p, Point q, Rectangle r) {
            double x1 = p.x, y1 = p.y, x2 = q.x, y2 = q.y;
            double dx = x2 - x1, dy = y2 - y1;
            java.util.List<double[]> hits = new java.util.ArrayList<>();

            // côtés verticaux (x = left, right)
            if (dx != 0) {
                double tLeft  = (r.x - x1) / dx;
                double yLeft  = y1 + tLeft * dy;
                if (tLeft >= 0 && tLeft <= 1 && yLeft >= r.y && yLeft <= r.y + r.height)
                    hits.add(new double[]{tLeft, r.x, yLeft});

                double tRight = (r.x + r.width - x1) / dx;
                double yRight = y1 + tRight * dy;
                if (tRight >= 0 && tRight <= 1 && yRight >= r.y && yRight <= r.y + r.height)
                    hits.add(new double[]{tRight, r.x + r.width, yRight});
            }

            // côtés horizontaux (y = top, bottom)
            if (dy != 0) {
                double tTop = (r.y - y1) / dy;
                double xTop = x1 + tTop * dx;
                if (tTop >= 0 && tTop <= 1 && xTop >= r.x && xTop <= r.x + r.width)
                    hits.add(new double[]{tTop, xTop, r.y});

                double tBottom = (r.y + r.height - y1) / dy;
                double xBottom = x1 + tBottom * dx;
                if (tBottom >= 0 && tBottom <= 1 && xBottom >= r.x && xBottom <= r.x + r.width)
                    hits.add(new double[]{tBottom, xBottom, r.y + r.height});
            }

            if (hits.isEmpty()) return q;

            // on veut le point le plus proche de p dans la direction de q (t minimal > 0)
            hits.sort(java.util.Comparator.comparingDouble(a -> a[0]));
            double[] h = hits.get(0);
            return new Point((int)Math.round(h[1]), (int)Math.round(h[2]));
        }

        // Donne les extrémités "clippées" sur les bords des rectangles des nœuds
        private Point[] clipEdgeToNodeBounds(Point aCenter, Point bCenter, Rectangle ra, Rectangle rb, int margin) {
            // on ajoute un petit margin pour que la flèche ne touche pas le bord
            Rectangle raM = new Rectangle(ra.x - margin, ra.y - margin, ra.width + 2*margin, ra.height + 2*margin);
            Rectangle rbM = new Rectangle(rb.x - margin, rb.y - margin, rb.width + 2*margin, rb.height + 2*margin);

            // depuis le centre du A vers B : sortie de A
            Point start = intersectLineWithRect(aCenter, bCenter, raM);
            // depuis le centre du B vers A : sortie de B, puis on repart dans l'autre sens
            Point endFromB = intersectLineWithRect(bCenter, aCenter, rbM);
            // convertir en point sur le bord de B dans la direction A->B
            Point end = endFromB;

            return new Point[]{ start, end };
        }

        private void layoutCircle() {
            int n = nodes.size();
            int cx = 520, cy = 330, r = 260;
            for (int i=0;i<n;i++) {
                double a = 2*Math.PI * i / n;
                int x = (int)(cx + r*Math.cos(a));
                int y = (int)(cy + r*Math.sin(a));
                pos.put(nodes.get(i), new Point(x,y));
            }
        }

        private Point toWorld(Point screen) {
            return new Point(
                    (int)((screen.x - pan.x) / zoom),
                    (int)((screen.y - pan.y) / zoom)
            );
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // appliquer pan/zoom
            g2.translate(pan.x, pan.y);
            g2.scale(zoom, zoom);

            // edges (d'abord)
            for (var from : edges.keySet()) {
                for (var to : edges.get(from)) {
                    drawEdge(g2, from, to, (focused==null || from.equals(focused)) ? new Color(170,170,190) : new Color(230,230,235));
                }
            }
            // nodes (après) pour que la flèche reste visible au bord
            for (String n : nodes) drawNode(g2, n, n.equals(focused));
        }

        private Rectangle nodeRect(Point c) { return new Rectangle(c.x-80, c.y-18, 160, 36); }

        private String hitTest(Point p) {
            for (var e : pos.entrySet()) {
                if (nodeRect(e.getValue()).contains(p)) return e.getKey();
            }
            return null;
        }

        private void drawNode(Graphics2D g2, String name, boolean focus) {
            Point c = pos.get(name);
            Rectangle r = nodeRect(c);

            boolean isExternal = name.startsWith("<external>");           // ex: "<external>#getX"
            boolean isSqrt     = "<external>#sqrt".equals(name);          // cible: ext#sqrt

            // Couleur de fond: focus > ext#sqrt > ext#* > normal
            Color fill = focus
                    ? new Color(70,130,180)         // bleu focus
                    : (isSqrt ? new Color(0xFF,0xD5,0x4D)        // doré pour ext#sqrt
                    : isExternal ? new Color(0xDF,0xE6,0xEF)     // gris clair externes
                    : new Color(220,226,235));      // normal

            g2.setColor(fill);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 18,18);

            // Bordure: dorée si ext#sqrt, sinon bordure standard
            g2.setColor(isSqrt ? new Color(0xB3,0x8F,0x00) : new Color(60,70,90));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 18,18);

            g2.setFont(new Font("Segoe UI", focus? Font.BOLD: Font.PLAIN, 12));

            // Label lisible: "<external>#getX" -> "ext #getX"
            String label = isExternal ? "ext " + name.substring(10) : name;

            FontMetrics fm = g2.getFontMetrics();
            int tx = r.x + (r.width - fm.stringWidth(label)) / 2;
            int ty = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2 - 1;

            g2.setColor(new Color(30,40,55));
            g2.drawString(label, tx, ty);
        }

        private void drawEdge(Graphics2D g2, String from, String to, Color col) {
            Point a = pos.get(from), b = pos.get(to);
            if (a == null || b == null) return;

            Rectangle ra = nodeRect(a);
            Rectangle rb = nodeRect(b);

            // 1) calculer les points aux bords des rectangles (au lieu des centres)
            Point[] ends = clipEdgeToNodeBounds(a, b, ra, rb, 4);
            Point aEdge = ends[0];
            Point bEdge = ends[1];

            // 2) raccourcir encore un peu pour la flèche (que la pointe dépasse légèrement)
            double ang = Math.atan2(bEdge.y - aEdge.y, bEdge.x - aEdge.x);
            int arrowLen = 14; // longueur de la flèche
            int backOff  = 2;  // petit retrait pour éviter de toucher le bord
            int bx = (int) Math.round(bEdge.x - backOff * Math.cos(ang));
            int by = (int) Math.round(bEdge.y - backOff * Math.sin(ang));

            // 3) tracer la ligne
            g2.setStroke(new BasicStroke(2.0f));
            g2.setColor(col);
            g2.drawLine(aEdge.x, aEdge.y, bx, by);

            // 4) dessiner la pointe de flèche au bord du nœud cible
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(0, 0);
            arrow.lineTo(-arrowLen, 5);
            arrow.lineTo(-arrowLen, -5);
            arrow.closePath();

            AffineTransform at = new AffineTransform();
            at.translate(bx, by);
            at.rotate(ang);
            g2.fill(at.createTransformedShape(arrow));
        }
    }
}

