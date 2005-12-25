package org.spacebar.escape;

import static org.spacebar.escape.common.Entity.*;
import static org.spacebar.escape.common.Level.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.gvt.GVTTreeWalker;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Characters;
import org.spacebar.escape.common.IntPair;
import org.spacebar.escape.common.Level;
import org.spacebar.escape.common.StyleStack;
import org.spacebar.escape.j2se.ResourceUtil;
import org.spacebar.escape.j2se.StyleStack2;
import org.w3c.dom.svg.SVGDocument;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

public class Level2PDF {
    private static final int BASE_TILE_SIZE = 32;

    private static final float PAD_FACTOR = 0.25f;

    final public static String id = "$Id$";

    final public static String creator = id
            .replaceAll("^\\$Id: (.*)\\$$", "$1");

    final static BaseFont BASE_FONT;
    static {
        ByteBuffer.HIGH_PRECISION = true;
        Document.compress = false; // XXX

        // get the font
        BaseFont f = null;
        {
            final String fontName = "Fixedsys500c.ttf";
            InputStream font = ResourceUtil.getLocalResourceAsStream(fontName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int d;
            try {
                while ((d = font.read()) != -1) {
                    baos.write(d);
                }
                byte fontData[] = baos.toByteArray();
                f = BaseFont.createFont(fontName, BaseFont.CP1252, true, true,
                        fontData, null);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (DocumentException e) {
                e.printStackTrace();
            }
        }
        BASE_FONT = f;
    }

    final static String parser = XMLResourceDescriptor.getXMLParserClassName();

    final static SAXSVGDocumentFactory svgDocFactory = new SAXSVGDocumentFactory(
            parser);

    final public static void main(String[] args) {
        try {
            // get level
            File f = new File(args[0]);
            Level l = new Level(new BitInputStream(new FileInputStream(f)));
            l.print(System.out);

            String basename = f.getName().replaceFirst("\\.esx$", "");

            makePDF(l, PageSize.LETTER, new FileOutputStream(basename + ".pdf"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param l
     * @param basename
     * @throws IOException
     */
    public static void makePDF(Level l, Rectangle page, OutputStream out) {
        float levAspect = (float) l.getWidth() / (float) l.getHeight();

        // do PDF stuff
        int margin = 18;

        // XXX: take into account title block?
        boolean landscape = levAspect >= 1.0;
        System.out.println("landscape: " + landscape);

        if (landscape && page.height() > page.width()) {
            page = page.rotate();
        }

        Document document = new Document(page, margin, margin, margin, margin);

        try {

            // we create a writer that listens to the document
            // and directs a PDF-stream to a file
            PdfWriter writer = PdfWriter.getInstance(document, out);

            // metadata
            document.addAuthor(StyleStack.removeStyle(l.getAuthor()));
            document.addTitle(StyleStack.removeStyle(l.getTitle()));
            document.addCreator(creator);
            document.addSubject("Escape");

            document.open();

            // title and author
            Font font = new Font(BASE_FONT, 16);

            String text = Characters.WHITE + l.getTitle() + Characters.GRAY
                    + " by " + Characters.BLUE + l.getAuthor();

            PdfPTable t = new PdfPTable(1);
            t.setWidthPercentage(100);
            PdfPCell cell = new PdfPCell(new Paragraph(formatText(text, font)));
            Color bannerC = new Color(34, 34, 68);
            cell.setBackgroundColor(bannerC);
            cell.setVerticalAlignment(com.lowagie.text.Element.ALIGN_MIDDLE);
            cell.setUseAscender(true);
            cell.setUseDescender(true);
            cell.setBorder(0);
            cell.setPaddingTop(4);
            cell.setPaddingBottom(4);
            t.addCell(cell);

            document.add(t);
            float rHeight = document.top() - document.bottom()
                    - t.getRowHeight(0);
            float rWidth = document.right() - document.left();

            System.out.println("rHeight: " + rHeight + ", rWidth: " + rWidth);

            // figure out how big to be
            System.out.println("level aspect: " + levAspect);
            float sAspect = rWidth / rHeight;
            System.out.println("space aspect: " + sAspect);
            boolean widthConstrained = levAspect > sAspect;

            float tileSize;
            float w;
            float h;
            float xOff;
            float yOff;
            if (widthConstrained) {
                tileSize = rWidth / (l.getWidth() + 2 * PAD_FACTOR);
            } else {
                tileSize = rHeight / (l.getHeight() + 2 * PAD_FACTOR);
            }

            float padding = tileSize * PAD_FACTOR;
            w = tileSize * l.getWidth();
            h = tileSize * l.getHeight();

            float masterScale = tileSize / BASE_TILE_SIZE;

            System.out.println("masterScale: " + masterScale);

            // one of the next two should be 0
            xOff = (rWidth - (w + 2 * padding)) / 2;
            yOff = (rHeight - (h + 2 * padding)) / 2;
            System.out.println("xOff: " + xOff + ", yOff: " + yOff);

            PdfContentByte cb = writer.getDirectContent();
            cb.saveState();

            // transform to fit into page
            AffineTransform af = new AffineTransform();
            af.translate(margin + xOff, margin + yOff);
            af.scale(masterScale, masterScale);
            cb.transform(af);

            layDownBlack(l, cb);
            cb.restoreState();
            cb.saveState();

            // create the level as a form XObject, so that patterns come out
            // nice
            PdfTemplate levelField = cb.createTemplate(l.getWidth()
                    * BASE_TILE_SIZE, l.getHeight() * BASE_TILE_SIZE);
            // PdfContentByte levelField = cb;
            levelField.saveState();

            // every level has T_FLOOR
            PdfPatternPainter brickPattern = createBrickPattern(levelField);
            layDownBrick(l, levelField, brickPattern);

            if (l.hasTile(Level.T_ROUGH)) {
                levelField.restoreState();
                levelField.saveState();
                PdfPatternPainter roughPattern = createRoughPattern(levelField);
                layDownRough(l, levelField, roughPattern);
            }

            levelField.restoreState();
            levelField.saveState();
            layDownTiles(l, levelField);
            layDownSprites(l, levelField);
            levelField.restoreState();
            
            // hit it
            af = new AffineTransform();
            af.translate(margin + padding + xOff, margin + padding + yOff);
            af.scale(masterScale, masterScale);
            double mat[] = new double[6];
            af.getMatrix(mat);
            cb.saveState();
//            cb.addTemplate(levelField, (float) mat[0], (float) mat[1],
//                    (float) mat[2], (float) mat[3], (float) mat[4],
//                    (float) mat[5]);
            cb.restoreState();
            cb.restoreState();

            PdfGState gs = new PdfGState();
            gs.setBlendMode(PdfGState.BM_NORMAL);
            gs.setFillOpacity(1.0f);
            gs.setStrokeOpacity(1.0f);
            cb.setGState(gs);
            
            document.close();
        } catch (DocumentException de) {
            System.err.println(de.getMessage());
        }

        // done
    }

    private static PdfPatternPainter createRoughPattern(PdfContentByte cb) {
        try {
            System.out.println("reading rough svg");
            SVGDocument doc = (SVGDocument) svgDocFactory.createDocument(null,
                    ResourceUtil.getLocalResourceAsStream("rough-pieces.svg"));
            GVTBuilder gvtb = new GVTBuilder();
            UserAgent ua = new UserAgentAdapter();
            GraphicsNode gn = gvtb.build(new BridgeContext(ua), doc);
            Rectangle2D bounds = gn.getBounds();
            PdfPatternPainter pat = cb.createPattern(
                    (float) (bounds.getX() + bounds.getWidth()),
                    (float) (bounds.getY() + bounds.getHeight()),
                    BASE_TILE_SIZE / 8, BASE_TILE_SIZE / 8);
            pat.setPatternMatrix(1, 0, 0, 1, -1.5f, .5f);
            Graphics2D g2 = pat.createGraphicsShapes(pat.getWidth(), pat
                    .getHeight());
            System.out.println("painting svg");
            gn.paint(g2);
            g2.dispose();

            return pat;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void layDownSprites(Level l, PdfContentByte cb) {
        // TODO Auto-generated method stub

    }

    static Color grayColors[] = new Color[] { new Color(75, 75, 75),
            new Color(127, 127, 127), new Color(137, 137, 137),
            new Color(159, 159, 159), new Color(103, 103, 103) };

    static Color redColors[] = new Color[] { new Color(79, 0, 0),
            new Color(162, 0, 0), new Color(180, 0, 0), new Color(206, 0, 0),
            new Color(121, 0, 0) };

    static Color blueColors[] = new Color[] { new Color(0, 0, 79),
            new Color(0, 0, 185), new Color(0, 0, 208), new Color(63, 63, 255),
            new Color(0, 0, 135) };

    static Color greenColors[] = new Color[] { new Color(7, 79, 0),
            new Color(7, 127, 0), new Color(5, 138, 0), new Color(5, 165, 0),
            new Color(7, 103, 0) };

    static Color goldColors[] = new Color[] { new Color(126, 126, 0),
            new Color(255, 247, 35), new Color(255, 255, 174),
            new Color(255, 255, 255), new Color(207, 199, 0) };

    private static void layDownTiles(Level l, PdfContentByte cb) {
        // blocks
        PdfPatternPainter blockPats[] = createBlockPatterns(cb);
        layDownBlockTile(l, cb, T_GREY, grayColors, blockPats);
        layDownBlockTile(l, cb, T_RED, redColors, blockPats);
        layDownBlockTile(l, cb, T_BLUE, blueColors, blockPats);
        layDownBlockTile(l, cb, T_GREEN, greenColors, blockPats);
        layDownBlockTile(l, cb, T_GOLD, goldColors, blockPats);

        // TODO broken
        
        
        // simple overlay
        layDownSimpleTile(l, cb, T_EXIT);
        layDownSimpleTile(l, cb, T_HOLE);
        layDownSimpleTile(l, cb, T_LASER);
        layDownSimpleTile(l, cb, T_STOP);
//        layDownSimpleTile(l, cb, T_TRANSPORT);
//        layDownSimpleTile(l, cb, T_TRAP2);
//        layDownSimpleTile(l, cb, T_TRAP1);
//        layDownSimpleTile(l, cb, T_HEARTFRAMER);
//        layDownSimpleTile(l, cb, T_SLEEPINGDOOR);
        /*
         * // colored overlays T_ELECTRIC
         * T_BUP T_BDOWN T_RUP T_RDOWN T_GUP T_GDOWN // bricks T_RED T_BLUE
         * T_GREY T_GREEN T_GOLD T_BROKEN // spheres T_BSPHERE T_RSPHERE
         * T_GSPHERE T_SPHERE // panels T_PANEL T_BPANEL T_RPANEL T_GPANEL //
         * arrows T_RIGHT T_LEFT T_UP T_DOWN // electric box T_ON T_OFF // other
         * arrows T_LR T_UD // 0/1 T_0 T_1 // wires T_NS T_NE T_NW T_SE T_SW
         * T_WE // button, lights, crossover T_BUTTON T_BLIGHT T_RLIGHT T_GLIGHT
         * T_TRANSPONDER T_NSWE // steel T_STEEL T_BSTEEL T_RSTEEL T_GSTEEL
         */
    }

    final static DecimalFormat svgFileFormatter = new DecimalFormat("00");

    private static void layDownBlockTile(Level l, PdfContentByte cb, byte tile,
            Color colors[], PdfPatternPainter pats[]) {
        if (!l.hasTile(tile)) {
            return;
        }

        cb.saveState();
        PdfPatternPainter tilePattern = createBlockPattern(cb, colors, pats);
        makePathsFromTile(l, cb, new byte[] { tile });
        cb.clip();
        cb.newPath();

        cb.setColorFill(colors[colors.length - 1]);
        levelPath(l, cb);
        cb.fill();

        levelPath(l, cb);
        cb.setPatternFill(tilePattern);
        cb.fill();

        cb.restoreState();
    }

    private static void layDownSimpleTile(Level l, PdfContentByte cb, byte tile) {
        if (!l.hasTile(tile)) {
            return;
        }

        cb.saveState();
        PdfPatternPainter tilePattern = createTilePattern(cb, tile);
        makePathsFromTile(l, cb, new byte[] { tile });
        
        cb.setPatternFill(tilePattern);
        cb.fill();
        cb.restoreState();
    }

    private static PdfPatternPainter createBlockPattern(PdfContentByte cb,
            Color colors[], PdfPatternPainter pats[]) {
        PdfPatternPainter pat = cb
                .createPattern(BASE_TILE_SIZE, BASE_TILE_SIZE);
        for (int i = 0; i < pats.length; i++) {
            // System.out.println(colors[i]);
            pat.rectangle(0, 0, BASE_TILE_SIZE, BASE_TILE_SIZE);
            pat.setPatternFill(pats[i], colors[i]);
            pat.fill();
        }
        return pat;
    }

    static PdfPatternPainter[] createBlockPatterns(PdfContentByte cb) {
        try {
            SVGDocument doc = (SVGDocument) svgDocFactory.createDocument(null,
                    ResourceUtil.getLocalResourceAsStream("block-pieces.svg"));
            GVTBuilder gvtb = new GVTBuilder();
            UserAgent ua = new UserAgentAdapter();

            PdfPatternPainter pats[] = new PdfPatternPainter[4];

            GraphicsNode gn = gvtb.build(new BridgeContext(ua), doc);
            GVTTreeWalker tw = new GVTTreeWalker(gn);
            for (int i = 0; i < 4; i++) {
                PdfPatternPainter pat = cb.createPattern(BASE_TILE_SIZE,
                        BASE_TILE_SIZE, null);
                pats[i] = pat;

                // System.out.println("node " + i);
                gn = tw.nextGraphicsNode();

                readAndStrokeBlocks(pat, gn);
            }

            return pats;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void readAndStrokeBlocks(PdfContentByte cb, GraphicsNode gn) {
        Shape s = gn.getOutline();

        PathIterator it = s.getPathIterator(null);

        float c[] = new float[6];
        while (!it.isDone()) {
            switch (it.currentSegment(c)) {
            case PathIterator.SEG_CLOSE:
                // System.out.println("close");
                cb.closePath();
                break;
            case PathIterator.SEG_CUBICTO:
                // System.out.println("cubic " + c[0] + " " + c[1] + " "
                // + c[2] + " " + c[3] + " " + c[4] + " " + c[5]);
                cb.curveTo(c[0], BASE_TILE_SIZE - c[1], c[2], BASE_TILE_SIZE
                        - c[3], c[4], BASE_TILE_SIZE - c[5]);
                break;
            case PathIterator.SEG_LINETO:
                // System.out.println("line " + c[0] + " " + c[1]);
                cb.lineTo(c[0], BASE_TILE_SIZE - c[1]);
                break;
            case PathIterator.SEG_MOVETO:
                // System.out.println("move " + c[0] + " " + c[1]);
                cb.moveTo(c[0], BASE_TILE_SIZE - c[1]);
                break;
            case PathIterator.SEG_QUADTO:
                // System.out.println("quad " + c[0] + " " + c[1] + " "
                // + c[2] + " " + c[3]);
                cb.curveTo(c[0], BASE_TILE_SIZE - c[1], c[2], BASE_TILE_SIZE
                        - c[3]);
                break;
            }
            it.next();
        }
        cb.fill();
    }

    private static PdfPatternPainter createTilePattern(PdfContentByte cb,
            byte tile) {
        try {
            System.out.println("reading " + tile + " svg");
            SVGDocument doc = (SVGDocument) svgDocFactory.createDocument(null,
                    ResourceUtil.getLocalResourceAsStream("tiles/"
                            + svgFileFormatter.format(tile) + ".svg"));
            GVTBuilder gvtb = new GVTBuilder();
            UserAgent ua = new UserAgentAdapter();
            GraphicsNode gn = gvtb.build(new BridgeContext(ua), doc);
            PdfPatternPainter pat = cb.createPattern(BASE_TILE_SIZE,
                    BASE_TILE_SIZE);
            Graphics2D g2 = pat.createGraphicsShapes(pat.getWidth(), pat
                    .getHeight());
            System.out.println("painting svg");
            gn.paint(g2);
            g2.dispose();
            return pat;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void layDownRough(Level l, PdfContentByte cb,
            PdfPatternPainter pat) {
        makePathsFromTile(l, cb, new byte[] { T_ROUGH });
        cb.clip();
        cb.newPath();

        levelPath(l, cb);
        cb.setColorFill(new Color(180, 180, 180));
        cb.fill();

        levelPath(l, cb);
        cb.setPatternFill(pat);
        cb.fill();
    }

    private static void layDownBrick(Level l, PdfContentByte cb,
            PdfPatternPainter pat) {
        // cut out black spots, but not rough
        makePathsFromTile(l, cb, new byte[] { T_FLOOR, T_EXIT, T_HOLE, T_LASER,
                T_PANEL, T_STOP, T_ELECTRIC, T_TRANSPORT, T_BUP, T_BDOWN,
                T_RUP, T_RDOWN, T_GUP, T_GDOWN, T_SPHERE, T_GSPHERE, T_RSPHERE,
                T_BSPHERE, T_TRAP2, T_TRAP1, T_RPANEL, T_BPANEL, T_GPANEL,
                T_HEARTFRAMER, T_SLEEPINGDOOR });

        cb.clip();
        cb.newPath();

        levelPath(l, cb);
        cb.setColorFill(new Color(195, 195, 195));
        cb.fill();

        levelPath(l, cb);
        cb.setPatternFill(pat);
        cb.fill();
    }

    private static void levelPath(Level l, PdfContentByte cb) {
        cb.rectangle(0, 0, l.getWidth() * BASE_TILE_SIZE, l.getHeight()
                * BASE_TILE_SIZE);
    }

    static class Pair extends IntPair {
        public Pair(int x, int y) {
            super(x, y);
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof IntPair) {
                IntPair i = (IntPair) obj;
                return x == i.x && y == i.y;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return x + 1000000 * y; // XXX: heh
        }
    }

    private static void makePathsFromTile(Level l, PdfContentByte cb,
            byte tiles[]) {

        boolean map[][] = makeTileMap(l, tiles);
        printMap(map);

        // zip across and find all the edges, jcreed-style!
        byte[][] edges = new byte[l.getHeight() + 1][l.getWidth() + 1];
        for (int y = 0; y < edges.length; y++) {
            for (int x = 0; x < edges[0].length; x++) {
                // edge generation!
                boolean ul = getVal(map, x - 1, y - 1);
                boolean ur = getVal(map, x, y - 1);
                boolean ll = getVal(map, x - 1, y);
                boolean lr = getVal(map, x, y);

                if (ul && !ur && !ll && lr) {
                    edges[y][x] = DIR_LEFT_RIGHT;
                } else if (!ul && ur && ll && !lr) {
                    edges[y][x] = DIR_UP_DOWN;
                } else if (!ul && ur) {
                    edges[y][x] = DIR_UP;
                } else if (ll && !lr) {
                    edges[y][x] = DIR_DOWN;
                } else if (!ur && lr) {
                    edges[y][x] = DIR_RIGHT;
                } else if (ul && !ll) {
                    edges[y][x] = DIR_LEFT;
                }
            }
        }

        System.out.println();
        printMap(edges, map);

        // make the paths list
        java.util.List<List<Pair>> paths = new ArrayList<List<Pair>>();

        Pair start = new Pair(0, 0);
        while ((start = getNextStart(edges, start)) != null) {
            // new path
            java.util.List<Pair> path = new ArrayList<Pair>();
            paths.add(path);

            path.add(start);

            int y = start.y;
            int x = start.x;

            byte dir;
            while ((dir = edges[y][x]) != DIR_NONE) {
                // move to the next spot
                switch (dir) {
                case DIR_UP_DOWN:
                    // up sounds good
                    edges[y][x] = DIR_DOWN;
                    y--;
                    break;
                case DIR_LEFT_RIGHT:
                    // left sounds good
                    edges[y][x] = DIR_RIGHT;
                    x--;
                    break;
                case DIR_DOWN:
                    edges[y][x] = DIR_NONE;
                    y++;
                    break;
                case DIR_UP:
                    edges[y][x] = DIR_NONE;
                    y--;
                    break;
                case DIR_LEFT:
                    edges[y][x] = DIR_NONE;
                    x--;
                    break;
                case DIR_RIGHT:
                    edges[y][x] = DIR_NONE;
                    x++;
                    break;
                }
                path.add(new Pair(x, y));
            }
        }

        for (Iterator<List<Pair>> iter = paths.iterator(); iter.hasNext();) {
            List path = iter.next();
            System.out.println("path: " + path);
        }

        List<List<Pair>> simplePaths = new ArrayList<List<Pair>>();
        // now, we have the segments, so get it down to corners
        for (Iterator<List<Pair>> iter = paths.iterator(); iter.hasNext();) {
            List path = iter.next();
            List<Pair> newPath = new ArrayList<Pair>();
            simplePaths.add(newPath);

            Iterator iter2 = path.iterator();
            Pair prev;
            Pair current = (Pair) iter2.next();
            newPath.add(current);

            byte oldDir = DIR_NONE;
            while (iter2.hasNext()) {
                prev = current;
                current = (Pair) iter2.next();

                int x = current.x;
                int y = current.y;
                int px = prev.x;
                int py = prev.y;

                int dx = x - px;
                int dy = y - py;

                byte dir;
                if (dy == 0) {
                    if (dx < 0) {
                        dir = DIR_LEFT;
                    } else {
                        dir = DIR_RIGHT;
                    }
                } else {
                    if (dy < 0) {
                        dir = DIR_UP;
                    } else {
                        dir = DIR_DOWN;
                    }
                }
                if (oldDir == DIR_NONE) {
                    oldDir = dir;
                }
                if (oldDir != dir) {
                    newPath.add(prev);
                    oldDir = dir;
                }
            }
        }

        int h = l.getHeight();
        for (Iterator<List<Pair>> iter = simplePaths.iterator(); iter.hasNext();) {
            List path = iter.next();
            System.out.println("simple path: " + path);

            Iterator i = path.iterator();
            Pair p = (Pair) i.next();
            cb.moveTo(p.x * BASE_TILE_SIZE, (h - p.y) * BASE_TILE_SIZE);
            while (i.hasNext()) {
                p = (Pair) i.next();
                cb.lineTo(p.x * BASE_TILE_SIZE, (h - p.y) * BASE_TILE_SIZE);
            }
            cb.closePath();
        }
    }

    private static Pair getNextStart(byte[][] edges, Pair start) {
        int startX = start.x;
        for (int y = start.y; y < edges.length; y++) {
            byte[] row = edges[y];
            if (y != start.y) {
                startX = 0;
            }
            for (int x = startX; x < row.length; x++) {
                if (row[x] != DIR_NONE) {
                    return new Pair(x, y);
                }
            }
        }
        return null;
    }

    private static void printMap(boolean[][] map) {
        for (int y = 0; y < map.length; y++) {
            boolean[] row = map[y];
            for (int x = 0; x < row.length; x++) {
                System.out.print(row[x] ? "X " : ". ");
            }
            System.out.println();
        }
    }

    private static void printMap(byte[][] edges, boolean[][] map) {
        for (int y = 0; y < edges.length; y++) {
            byte[] row = edges[y];
            for (int x = 0; x < row.length; x++) {
                switch (row[x]) {
                case DIR_NONE:
                    System.out.print("  ");
                    break;
                case DIR_RIGHT:
                    System.out.print("\u2192 ");
                    break;
                case DIR_LEFT:
                    System.out.print("\u2190 ");
                    break;
                case DIR_DOWN:
                    System.out.print("\u2193 ");
                    break;
                case DIR_UP:
                    System.out.print("\u2191 ");
                    break;
                case DIR_LEFT_RIGHT:
                    System.out.print("\u2194 ");
                    break;
                case DIR_UP_DOWN:
                    System.out.print("\u2195 ");
                    break;
                }
            }
            System.out.println();
            if (y < edges.length - 1) {
                for (int x = 0; x < row.length - 1; x++) {
                    System.out.print(map[y][x] ? " X" : "  ");
                }
                System.out.println();
            }
        }
    }

    private static boolean getVal(boolean[][] map, int x, int y) {
        if (x < 0 || y < 0 || y >= map.length || x >= map[0].length) {
            return false;
        } else {
            return map[y][x];
        }
    }

    private static boolean[][] makeTileMap(Level l, byte tiles[]) {
        int w = l.getWidth();
        int h = l.getHeight();

        boolean space[][] = new boolean[h][w];
        for (int y = 0; y < space.length; y++) {
            boolean[] row = space[y];
            for (int x = 0; x < row.length; x++) {
                COORDINATE: for (int i = 0; i < tiles.length; i++) {
                    byte t = l.tileAt(x, y);
                    if (t == tiles[i]) {
                        row[x] = true;
                        continue COORDINATE;
                    }
                }
            }
        }
        return space;
    }

    private static PdfPatternPainter createBrickPattern(PdfContentByte cb) {
        try {
            System.out.println("reading bricks svg");
            SVGDocument doc = (SVGDocument) svgDocFactory.createDocument(null,
                    ResourceUtil.getLocalResourceAsStream("brick-pieces.svg"));
            GVTBuilder gvtb = new GVTBuilder();
            UserAgent ua = new UserAgentAdapter();
            GraphicsNode gn = gvtb.build(new BridgeContext(ua), doc);
            PdfPatternPainter pat = cb.createPattern(BASE_TILE_SIZE + 5,
                    BASE_TILE_SIZE, BASE_TILE_SIZE, BASE_TILE_SIZE);
            pat.setPatternMatrix(1, 0, 0, 1, -5, 4);
            Graphics2D g2 = pat.createGraphicsShapes(pat.getWidth(), pat
                    .getHeight());
            System.out.println("painting svg");
            gn.paint(g2);
            g2.dispose();

            return pat;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void layDownBlack(Level l, PdfContentByte cb) {
        cb.rectangle(0, 0, (l.getWidth() + 2 * PAD_FACTOR) * BASE_TILE_SIZE, (l
                .getHeight() + 2 * PAD_FACTOR)
                * BASE_TILE_SIZE);
        cb.fill();
    }

    static Phrase formatText(String text, Font f) {
        StyleStack2 s = new StyleStack2();
        Phrase p = new Phrase();

        StringBuilder str = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '^') {
                i++;
                ch = text.charAt(i);
                switch (ch) {
                case '^':
                    str.append(ch);
                    break;
                case '<':
                    addToPhrase(f, s, p, str);
                    s.pop();
                    break;
                default:
                    addToPhrase(f, s, p, str);
                    s.push(ch);
                    break;
                }
            } else {
                str.append(ch);
            }
        }
        addToPhrase(f, s, p, str);
        return p;
    }

    private static void addToPhrase(Font f, StyleStack2 s, Phrase p,
            StringBuilder str) {
        if (str.length() != 0) {
            System.out.println("making new chunk: " + str);
            Font f2 = new Font(f);
            f2.setColor(s.getAWTColor());
            Chunk c = new Chunk(str.toString(), f2);
            c.setTextRenderMode(PdfContentByte.TEXT_RENDER_MODE_FILL_STROKE,
                    0.1f, Color.BLACK);
            str.delete(0, str.length());
            p.add(c);
        }
    }
}
