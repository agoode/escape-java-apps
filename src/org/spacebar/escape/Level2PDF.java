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
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.gvt.GVTTreeWalker;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.spacebar.escape.common.*;
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
        // Document.compress = false; // XXX

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

            // create the level as a form XObject, so that patterns come out
            // nice
            PdfTemplate levelField = cb.createTemplate(l.getWidth()
                    * BASE_TILE_SIZE, l.getHeight() * BASE_TILE_SIZE);
            levelField.saveState();

            // every level has T_FLOOR
            PdfPatternPainter brickPattern = createBrickPattern(levelField);
            layDownBrick(l, levelField, brickPattern);
            levelField.restoreState();
            levelField.saveState();

            if (l.hasTile(Level.T_ROUGH)) {
                PdfPatternPainter roughPattern = createRoughPattern(levelField);
                layDownRough(l, levelField, roughPattern);
                levelField.restoreState();
                levelField.saveState();
            }

            layDownTransparency(l, levelField);
            layDownTiles(l, levelField);
            levelField.restoreState();
            layDownSprites(l, levelField);
            drawLaser(l, levelField);

            // hit it
            af = new AffineTransform();
            af.translate(margin + padding + xOff, margin + padding + yOff);
            af.scale(masterScale, masterScale);
            double mat[] = new double[6];
            af.getMatrix(mat);

            levelField.saveState();

            cb.addTemplate(levelField, (float) mat[0], (float) mat[1],
                    (float) mat[2], (float) mat[3], (float) mat[4],
                    (float) mat[5]);
            levelField.restoreState();

            document.close();
        } catch (DocumentException de) {
            System.err.println(de.getMessage());
        }

        // done
    }

    private static void drawLaser(Level l, PdfContentByte cb) {
        // TODO Auto-generated method stub

    }

    private static void layDownTransparency(Level l, PdfTemplate levelField) {
        /*
         * create the knockout layer, and paint electric, up blocks, down
         * blocks, and transporter (later arrows?)
         */

        if (!(l.hasTile(T_ELECTRIC) || l.hasTile(T_BUP) || l.hasTile(T_BDOWN)
                || l.hasTile(T_RUP) || l.hasTile(T_RDOWN) || l.hasTile(T_GUP)
                || l.hasTile(T_GDOWN) || l.hasTile(T_TRANSPORT) || l
                .hasTile(T_HOLE))) {
            return;
        }

        PdfTemplate t = levelField.createTemplate(levelField.getWidth(),
                levelField.getHeight());
        PdfTransparencyGroup tg = new PdfTransparencyGroup();
        tg.setKnockout(true);
        t.setGroup(tg);

        // non-knockout group for fancy effect
        PdfTemplate ct = t.createTemplate(levelField.getWidth(), levelField
                .getHeight());
        PdfTransparencyGroup tg2 = new PdfTransparencyGroup();
        tg2.setKnockout(false);
        ct.setGroup(tg2);

        // blending mode for some colors
        ct.saveState();
        PdfGState gs = new PdfGState();
        // gs.setBlendMode(PdfGState.BM_OVERLAY);
        gs.setBlendMode(BM_COLOR);
        ct.setGState(gs);

        // colors
        layDownColor(l, ct, new byte[] { T_ELECTRIC }, new Color(255, 216, 0));

        gs = new PdfGState();
        gs.setBlendMode(PdfGState.BM_SOFTLIGHT);
        ct.setGState(gs);

        layDownColor(l, ct, new byte[] { T_RUP, T_RDOWN }, new Color(255, 0, 0));
        layDownColor(l, ct, new byte[] { T_GUP, T_GDOWN }, new Color(0, 255, 0));
        layDownColor(l, ct, new byte[] { T_BUP, T_BDOWN }, new Color(0, 0, 255));

        ct.restoreState();

        // colored shadow
        layDownTilesByName(l, ct, new byte[] { T_BUP, T_RUP, T_GUP },
                "up-shadow.svg");

        // add this group to the knockout group
        t.addTemplate(ct, 0, 0);

        // transport (easy)
        layDownSimpleTile(l, t, T_TRANSPORT);

        // hole
        layDownSimpleTile(l, t, T_HOLE);

        // now, knockout the color with this!
        layDownTilesByName(l, t, new byte[] { T_BUP, T_RUP, T_GUP },
                "up-pieces.svg");

        // bam
        levelField.addTemplate(t, 0, 0);
    }

    private static PdfPatternPainter createRoughPattern(PdfContentByte cb) {
        try {
            SVGDocument doc = loadSVG("rough-pieces.svg");

            BridgeContext bc = new BridgeContext(new UserAgentAdapter());
            GVTBuilder gvtb = new GVTBuilder();

            GraphicsNode gn = gvtb.build(bc, doc);
            Rectangle2D bounds = gn.getBounds();
            PdfPatternPainter pat = cb.createPattern(
                    (float) (bounds.getX() + bounds.getWidth()),
                    (float) (bounds.getY() + bounds.getHeight()),
                    BASE_TILE_SIZE / 8, BASE_TILE_SIZE / 8);
            pat.setPatternMatrix(1, 0, 0, 1, -1.5f, .5f);
            Graphics2D g2 = pat.createGraphicsShapes(pat.getWidth(), pat
                    .getHeight());
            gn.paint(g2);
            g2.dispose();

            return pat;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static SVGDocument loadSVG(String name) throws IOException {
        // TODO: caching
        System.out.println("reading " + name);
        SVGDocument doc = (SVGDocument) svgDocFactory.createDocument(null,
                ResourceUtil.getLocalResourceAsStream(name));
        return doc;
    }

    private static void layDownSprites(Level l, PdfContentByte cb) {
        // TODO Auto-generated method stub

    }

    final static Color grayColors[] = new Color[] { new Color(127, 127, 127),
            new Color(137, 137, 137), new Color(159, 159, 159),
            new Color(75, 75, 75), new Color(103, 103, 103) };

    final static Color redColors[] = new Color[] { new Color(162, 0, 0),
            new Color(180, 0, 0), new Color(206, 0, 0), new Color(79, 0, 0),
            new Color(121, 0, 0) };

    final static Color blueColors[] = new Color[] { new Color(0, 0, 185),
            new Color(0, 0, 208), new Color(63, 63, 255), new Color(0, 0, 79),
            new Color(0, 0, 135) };

    final static Color greenColors[] = new Color[] { new Color(7, 127, 0),
            new Color(5, 138, 0), new Color(5, 165, 0), new Color(7, 79, 0),
            new Color(7, 103, 0) };

    final static Color goldColors[] = new Color[] { new Color(255, 247, 35),
            new Color(255, 254, 103), new Color(255, 255, 255),
            new Color(126, 126, 0), new Color(207, 199, 0) };

    final static Color graySColors[] = new Color[] { new Color(139, 139, 139),
            new Color(195, 195, 195), new Color(96, 96, 96),
            new Color(106, 106, 106), new Color(76, 76, 76),
            new Color(39, 39, 39) };

    final static Color blueSColors[] = new Color[] { new Color(67, 90, 140),
            new Color(195, 195, 195), new Color(39, 73, 148),
            new Color(67, 90, 140), new Color(38, 61, 111),
            new Color(39, 39, 39) };

    final static Color redSColors[] = new Color[] { new Color(132, 75, 75),
            new Color(195, 195, 195), new Color(136, 51, 51),
            new Color(132, 75, 75), new Color(103, 46, 46),
            new Color(51, 51, 51) };

    final static Color greenSColors[] = new Color[] { new Color(83, 125, 83),
            new Color(195, 195, 195), new Color(62, 126, 62),
            new Color(84, 126, 84), new Color(55, 97, 55),
            new Color(39, 39, 39) };

    private static void layDownTiles(Level l, PdfContentByte cb) {
        // blocks
        if (l.hasTile(T_GREY) || l.hasTile(T_RED) || l.hasTile(T_BLUE)
                || l.hasTile(T_GREEN) || l.hasTile(T_GOLD)
                || l.hasTile(T_BROKEN)) {
            PdfTemplate blockTemps[] = createBlockTemplates(cb);
            layDownBlockTile(l, cb, T_GREY, grayColors, blockTemps);
            layDownBlockTile(l, cb, T_RED, redColors, blockTemps);
            layDownBlockTile(l, cb, T_BLUE, blueColors, blockTemps);
            layDownBlockTile(l, cb, T_GREEN, greenColors, blockTemps);
            layDownBlockTile(l, cb, T_GOLD, goldColors, blockTemps);

            // broken is gray + extra stuff
            layDownBlockTile(l, cb, T_BROKEN, grayColors, blockTemps);
            layDownTilesByName(l, cb, new byte[] { T_BROKEN },
                    "broken-pieces.svg");
        }

        // spheres
        if (l.hasTile(T_SPHERE) || l.hasTile(T_RSPHERE) || l.hasTile(T_GSPHERE)
                || l.hasTile(T_BSPHERE)) {
            PdfTemplate sphereTemps[] = createSphereTemplates(cb);
            layDownSphereTile(l, cb, T_SPHERE, graySColors, sphereTemps);
            layDownSphereTile(l, cb, T_RSPHERE, redSColors, sphereTemps);
            layDownSphereTile(l, cb, T_GSPHERE, greenSColors, sphereTemps);
            layDownSphereTile(l, cb, T_BSPHERE, blueSColors, sphereTemps);
        }

        // simple overlay
        layDownSimpleTile(l, cb, T_EXIT);
        layDownSimpleTile(l, cb, T_STOP);
        layDownSimpleTile(l, cb, T_HEARTFRAMER);
        layDownSimpleTile(l, cb, T_SLEEPINGDOOR);
        layDownSimpleTile(l, cb, T_TRAP1);
        layDownSimpleTile(l, cb, T_TRAP2);
        layDownSimpleTile(l, cb, T_LASER);

        // panels
        layDownGrayPanel(l, cb, T_PANEL);
        layDownColorPanel(l, cb, T_RPANEL, new Color(130, 59, 20, 80));
        layDownColorPanel(l, cb, T_GPANEL, new Color(49, 119, 31, 80));
        layDownColorPanel(l, cb, T_BPANEL, new Color(39, 61, 112, 80));

        // arrows
        byte arrowTiles[] = new byte[] { T_RIGHT, T_LEFT, T_UP, T_DOWN };
        drawSolid(l, cb, arrowTiles, new Color(177, 177, 177));
        layDownTilesByName(l, cb, arrowTiles, "arrow-back.svg");
        layDownSimpleTile(l, cb, T_RIGHT);
        layDownSimpleTile(l, cb, T_LEFT);
        layDownSimpleTile(l, cb, T_UP);
        layDownSimpleTile(l, cb, T_DOWN);

        // electric box
        layDownSimpleTile(l, cb, T_ON); // XXX: need to optimize background
        layDownSimpleTile(l, cb, T_OFF);

        // twisty
        byte twistTiles[] = new byte[] { T_LR, T_UD };
        drawSolid(l, cb, twistTiles, new Color(85, 85, 85));
        layDownTilesByName(l, cb, twistTiles, "twist-back.svg");
        layDownSimpleTile(l, cb, T_LR);
        layDownSimpleTile(l, cb, T_UD);

        // 0/1
        byte numTiles[] = new byte[] { T_0, T_1 };
        drawSolid(l, cb, numTiles, new Color(142, 142, 142));
        layDownTilesByName(l, cb, numTiles, "num-back.svg");
        layDownSimpleTile(l, cb, T_0);
        layDownSimpleTile(l, cb, T_1);

        // for wires, complex:

        // must draw the individual tiles
        drawSolid(l, cb,
                new byte[] { T_NS, T_NE, T_NW, T_SE, T_SW, T_WE, T_BUTTON,
                        T_BLIGHT, T_RLIGHT, T_GLIGHT, T_NSWE, T_TRANSPONDER },
                new Color(140, 140, 140));
        layDownSimpleTile(l, cb, T_NS);
        layDownSimpleTile(l, cb, T_NE);
        layDownSimpleTile(l, cb, T_NW);
        layDownSimpleTile(l, cb, T_SE);
        layDownSimpleTile(l, cb, T_SW);
        layDownSimpleTile(l, cb, T_WE);

        layDownTilesByName(l, cb, new byte[] { T_BUTTON, T_BLIGHT, T_RLIGHT,
                T_GLIGHT, T_NSWE, T_TRANSPONDER }, "crossover.svg");

        // then draw the wire in one pass
        // XXX: how to do this?

        // then the things on top

        /*
         * // steel
         * T_STEEL T_BSTEEL T_RSTEEL T_GSTEEL
         */
    }

    private static boolean[][] drawSolid(Level l, PdfContentByte cb,
            byte[] tiles, Color c) {
        boolean whereToDraw[][] = makePathsFromTile(l, cb, tiles);
        cb.setColorFill(c);
        cb.fill();

        return whereToDraw;
    }

    private static void layDownColorPanel(Level l, PdfContentByte cb,
            byte tile, Color color) {
        if (!l.hasTile(tile)) {
            return;
        }

        // create tile
        PdfTemplate tileTemplate = cb.createTemplate(BASE_TILE_SIZE,
                BASE_TILE_SIZE);

        tileTemplate.saveState();

        // set up the colors
        // alpha is for fill, but not stroke
        tileTemplate.setColorFill(color);
        tileTemplate.setColorStroke(color);
        PdfGState gs = new PdfGState();
        gs.setFillOpacity(color.getAlpha() / 255f);
        tileTemplate.setGState(gs);

        drawPanelPath(tileTemplate, 8.5f);

        tileTemplate.restoreState();

        // go
        boolean whereToDraw[][] = makeTileMap(l, new byte[] { tile });

        drawTiles(l, cb, tileTemplate, whereToDraw);
    }

    private static void drawPanelPath(PdfTemplate tileTemplate, float radius) {
        PdfTransparencyGroup tg = new PdfTransparencyGroup();
        tg.setKnockout(true);
        tileTemplate.setGroup(tg);

        tileTemplate.arc(-radius + BASE_TILE_SIZE / 2, -radius + BASE_TILE_SIZE
                / 2, radius + BASE_TILE_SIZE / 2, radius + BASE_TILE_SIZE / 2,
                0, 360);
        tileTemplate.fillStroke();
    }

    private static void layDownGrayPanel(Level l, PdfContentByte cb, byte tile) {
        if (!l.hasTile(tile)) {
            return;
        }

        // create tile
        PdfTemplate tileTemplate = cb.createTemplate(BASE_TILE_SIZE,
                BASE_TILE_SIZE);
        tileTemplate.saveState();

        // set up the colors
        float gray = 0;
        tileTemplate.setGrayFill(gray);
        tileTemplate.setGrayStroke(gray);

        PdfGState gs = new PdfGState();
        gs.setFillOpacity(127 / 255f);
        gs.setStrokeOpacity(64 / 255f);
        tileTemplate.setGState(gs);

        drawPanelPath(tileTemplate, 9.5f);
        tileTemplate.restoreState();

        // go
        boolean whereToDraw[][] = makeTileMap(l, new byte[] { tile });

        drawTiles(l, cb, tileTemplate, whereToDraw);
    }

    private static void layDownSphereTile(Level l, PdfContentByte cb,
            byte tile, Color[] colors, PdfTemplate[] temps) {
        if (!l.hasTile(tile)) {
            return;
        }

        cb.saveState();
        PdfTemplate tileTemplate = createColorTemplate(cb, colors, temps);

        // draw the background
        boolean whereToDraw[][] = makeTileMap(l, new byte[] { tile });

        // draw each tile
        drawTiles(l, cb, tileTemplate, whereToDraw);

        cb.restoreState();
    }

    private static PdfTemplate[] createSphereTemplates(PdfContentByte cb) {
        try {
            SVGDocument doc = loadSVG("sphere-pieces.svg");

            PdfTemplate temps[] = new PdfTemplate[6];

            BridgeContext bc = new BridgeContext(new UserAgentAdapter());
            GVTBuilder gvtb = new GVTBuilder();

            GraphicsNode gn = gvtb.build(bc, doc);
            GVTTreeWalker tw = new GVTTreeWalker(gn);
            gn = tw.nextGraphicsNode();
            for (int i = 0; i < temps.length; i++) {
                PdfTemplate t = cb.createTemplate(BASE_TILE_SIZE,
                        BASE_TILE_SIZE);
                temps[i] = t;

                // System.out.println("node " + i);

                gn = tw.nextGraphicsNode();
                readAndStrokeBlocks(t, gn);
            }

            return temps;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void layDownTilesByName(Level l, PdfContentByte cb,
            byte tiles[], String name) {

        // check
        for (int i = 0; i < tiles.length; i++) {
            byte t = tiles[i];
            if (l.hasTile(t)) {
                break;
            }
            return;
        }

        PdfTemplate tileTemplate = createTileTemplate(cb, name);
        boolean whereToDraw[][] = makeTileMap(l, tiles);

        drawTiles(l, cb, tileTemplate, whereToDraw);
    }

    private static PdfTemplate createTileTemplate(PdfContentByte cb, String name) {
        try {
            SVGDocument doc = loadSVG(name);
            PdfTemplate t = createTileTemplate(cb, doc);
            return t;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    final static PdfName BM_COLOR = new PdfName("Color");

    private static void layDownColor(Level l, PdfContentByte cb, byte[] tiles,
            Color color) {
        cb.setColorFill(color);

        makePathsFromTile(l, cb, tiles);
        cb.fill();
    }

    final static DecimalFormat svgFileFormatter = new DecimalFormat("00");

    private static void layDownBlockTile(Level l, PdfContentByte cb, byte tile,
            Color colors[], PdfTemplate temps[]) {
        if (!l.hasTile(tile)) {
            return;
        }

        cb.saveState();
        PdfTemplate tileTemplate = createColorTemplate(cb, colors, temps);

        // draw the background
        boolean whereToDraw[][] = drawSolid(l, cb, new byte[] { tile },
                colors[colors.length - 1]);

        // draw each tile
        drawTiles(l, cb, tileTemplate, whereToDraw);

        cb.restoreState();
    }

    private static void drawTiles(Level l, PdfContentByte cb,
            PdfTemplate tileTemplate, boolean[][] whereToDraw) {
        for (int y = 0; y < whereToDraw.length; y++) {
            boolean[] row = whereToDraw[y];
            for (int x = 0; x < row.length; x++) {
                if (row[x]) {
                    cb.addTemplate(tileTemplate, x * BASE_TILE_SIZE, (l
                            .getHeight() - 1)
                            * BASE_TILE_SIZE - y * BASE_TILE_SIZE);
                }
            }
        }
    }

    private static void layDownSimpleTile(Level l, PdfContentByte cb, byte tile) {
        if (!l.hasTile(tile)) {
            return;
        }

        PdfTemplate tileTemplate = createTileTemplate(cb, tile);
        boolean whereToDraw[][] = makeTileMap(l, new byte[] { tile });

        drawTiles(l, cb, tileTemplate, whereToDraw);
    }

    private static PdfTemplate createColorTemplate(PdfContentByte cb,
            Color colors[], PdfTemplate temps[]) {
        PdfTemplate t = cb.createTemplate(BASE_TILE_SIZE, BASE_TILE_SIZE);
        for (int i = 0; i < temps.length; i++) {
            // System.out.println(colors[i]);
            t.setColorFill(colors[i]);
            t.addTemplate(temps[i], 0, 0);
        }
        return t;
    }

    static PdfTemplate[] createBlockTemplates(PdfContentByte cb) {
        try {
            SVGDocument doc = loadSVG("block-pieces.svg");

            PdfTemplate temps[] = new PdfTemplate[4];

            BridgeContext bc = new BridgeContext(new UserAgentAdapter());
            GVTBuilder gvtb = new GVTBuilder();

            GraphicsNode gn = gvtb.build(bc, doc);
            GVTTreeWalker tw = new GVTTreeWalker(gn);
            gn = tw.nextGraphicsNode();
            for (int i = 0; i < temps.length; i++) {
                PdfTemplate t = cb.createTemplate(BASE_TILE_SIZE,
                        BASE_TILE_SIZE);
                temps[i] = t;

                // System.out.println("node " + i);
                gn = tw.nextGraphicsNode();

                readAndStrokeBlocks(t, gn);
            }

            return temps;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void readAndStrokeBlocks(PdfContentByte cb, GraphicsNode gn) {
        // System.out.println(gn);
        // System.out.println(gn.getBounds());

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

    private static PdfTemplate createTileTemplate(PdfContentByte cb, byte tile) {
        try {
            SVGDocument doc = loadSVG("tiles/" + svgFileFormatter.format(tile)
                    + ".svg");
            PdfTemplate t = createTileTemplate(cb, doc);
            return t;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static PdfTemplate createTileTemplate(PdfContentByte cb,
            SVGDocument doc) {
        BridgeContext bc = new BridgeContext(new UserAgentAdapter());
        GVTBuilder gvtb = new GVTBuilder();

        GraphicsNode gn = gvtb.build(bc, doc);
        PdfTemplate t = cb.createTemplate(BASE_TILE_SIZE, BASE_TILE_SIZE);
        Graphics2D g2 = t.createGraphicsShapes(t.getWidth(), t.getHeight());
        gn.paint(g2);
        g2.dispose();
        return t;
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

    private static boolean[][] makePathsFromTile(Level l, PdfContentByte cb,
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
        return map;
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
            SVGDocument doc = loadSVG("brick-pieces.svg");

            BridgeContext bc = new BridgeContext(new UserAgentAdapter());
            GVTBuilder gvtb = new GVTBuilder();

            GraphicsNode gn = gvtb.build(bc, doc);
            PdfPatternPainter pat = cb.createPattern(BASE_TILE_SIZE + 5,
                    BASE_TILE_SIZE, BASE_TILE_SIZE, BASE_TILE_SIZE);
            pat.setPatternMatrix(1, 0, 0, 1, -5, 4);
            Graphics2D g2 = pat.createGraphicsShapes(pat.getWidth(), pat
                    .getHeight());
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
