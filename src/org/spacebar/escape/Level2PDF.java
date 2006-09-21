package org.spacebar.escape;

import static org.spacebar.escape.common.Entity.*;
import static org.spacebar.escape.common.Level.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
     * @param page
     * @param out
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
            // and directs a PDF-stream to a stream
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

            // every level has T_FLOOR, if it has T_EXIT
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
            drawLaser(l, levelField);
            layDownSprites(l, levelField);

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
        IntTriple laser = l.getLaser();

        if (laser == null) {
            return;
        }

        float lx = laser.x * BASE_TILE_SIZE;
        float ly = (l.getHeight() - laser.y) * BASE_TILE_SIZE;

        float px = l.getPlayerX() * BASE_TILE_SIZE;
        float py = (l.getHeight() - l.getPlayerY()) * BASE_TILE_SIZE;

        switch (laser.d) {
        case Entity.DIR_DOWN:
            lx += BASE_TILE_SIZE / 2f;
            ly -= BASE_TILE_SIZE - 0.5f;
            px += BASE_TILE_SIZE / 2f;
            py -= BASE_TILE_SIZE / 2f;

            break;
        case Entity.DIR_UP:
            lx += BASE_TILE_SIZE / 2f;
            ly -= 0.5f;
            px += BASE_TILE_SIZE / 2f;
            py -= BASE_TILE_SIZE / 2f;
            break;
        case Entity.DIR_RIGHT:
            lx += BASE_TILE_SIZE - 0.5f;
            ly -= BASE_TILE_SIZE / 2f;
            px += BASE_TILE_SIZE / 2f;
            py -= BASE_TILE_SIZE / 2f;

            break;
        case Entity.DIR_LEFT:
            lx += 0.5f;
            ly -= BASE_TILE_SIZE / 2f;
            px += BASE_TILE_SIZE / 2f;
            py -= BASE_TILE_SIZE / 2f;
            break;
        }

        cb.setColorStroke(Color.RED);
        cb.setLineWidth(2.0f);
        cb.moveTo(lx, ly);
        cb.lineTo(px, py);
        cb.stroke();

        cb.setColorStroke(Color.WHITE);
        cb.setLineWidth(0.67f);
        cb.moveTo(lx, ly);
        cb.lineTo(px, py);
        cb.stroke();
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

    private static Map<String, SVGDocument> svgDocMap = new HashMap<String, SVGDocument>();

    private static SVGDocument loadSVG(String name) throws IOException {
        if (!svgDocMap.containsKey(name)) {
            System.out.println("reading " + name);
            SVGDocument doc = (SVGDocument) svgDocFactory.createDocument(null,
                    ResourceUtil.getLocalResourceAsStream(name));
            svgDocMap.put(name, doc);
        }
        return svgDocMap.get(name);
    }

    private static void layDownSprites(Level l, PdfContentByte cb) {
        // draw in correct z-order
        List<List<Entity>> entityList = new ArrayList<List<Entity>>();
        for (int y = 0; y < l.getHeight(); y++) {
            entityList.add(new ArrayList<Entity>());
        }

        for (int i = 0; i < l.getBotCount(); i++) {
            Bot b = l.getBot(i);
            entityList.get(b.getY()).add(b);
        }

        Player p = l.getPlayer();
        entityList.get(p.getY()).add(p);

        Map<String, PdfTemplate> sprites = new HashMap<String, PdfTemplate>();

        // draw
        int y = l.getHeight() - 1;
        for (List<Entity> row : entityList) {
            for (Entity e : row) {
                int x = e.getX();
                if (e.isPlayer()) {
                    // draw player
                    if (l.isDead()) {
                        drawSprite(cb, x, y, sprites, "animation/lasered2.svg");
                    } else {
                        drawSprite(cb, x, y, sprites,
                                "animation/walk_forward_0.svg");
                    }
                } else {
                    Bot b = (Bot) e;
                    byte type = b.getBotType();
                    switch (type) {
                    case Entity.B_BROKEN:
                        drawSprite(cb, x, y, sprites, "animation/deadrobot.svg");
                        break;
                    case Entity.B_DALEK:
                        drawSprite(cb, x, y, sprites, "common-bot.svg");
                        drawSprite(cb, x, y, sprites,
                                "animation/dalek_forward_0.svg");
                        break;
                    case Entity.B_DALEK_ASLEEP:
                        drawSprite(cb, x, y, sprites, "common-sleep-bot.svg");
                        drawSprite(cb, x, y, sprites,
                                "animation/dalek_asleep.svg");
                        break;
                    case Entity.B_HUGBOT:
                        drawSprite(cb, x, y, sprites, "common-bot.svg");
                        drawSprite(cb, x, y, sprites,
                                "animation/hugbot_forward_0.svg");
                        break;
                    case Entity.B_HUGBOT_ASLEEP:
                        drawSprite(cb, x, y, sprites, "common-sleep-bot.svg");
                        drawSprite(cb, x, y, sprites,
                                "animation/hugbot_asleep.svg");
                        break;
                    default:
                        // bomb?
                        if (type >= Entity.B_BOMB_0
                                && type <= Entity.B_BOMB_MAX) {
                            drawSprite(cb, x, y, sprites,
                                    "animation/bomb_still.svg");
                        }
                        break;

                    }
                }
            }
            y--;
        }
    }

    private static void drawSprite(PdfContentByte cb, int x, int y,
            Map<String, PdfTemplate> sprites, String name) {
        PdfTemplate t = getSpriteTemplate(cb, sprites, name);
        cb.addTemplate(t, x * BASE_TILE_SIZE, y * BASE_TILE_SIZE);
    }

    private static PdfTemplate getSpriteTemplate(PdfContentByte cb,
            Map<String, PdfTemplate> sprites, String name) {
        if (!sprites.containsKey(name)) {
            PdfTemplate t = createSpriteTemplate(cb, name);
            sprites.put(name, t);
        }

        return sprites.get(name);
    }

    private static PdfTemplate createSpriteTemplate(PdfContentByte cb,
            String name) {
        SVGDocument doc = null;
        try {
            doc = loadSVG(name);
        } catch (IOException e) {
            e.printStackTrace();
        }

        BridgeContext bc = new BridgeContext(new UserAgentAdapter());
        GVTBuilder gvtb = new GVTBuilder();

        GraphicsNode gn = gvtb.build(bc, doc);

        Rectangle2D bounds = gn.getBounds();
        PdfTemplate t = cb.createTemplate((float) (bounds.getX() + bounds
                .getWidth()), (float) (bounds.getY() + bounds.getHeight()));

        Graphics2D g2 = t.createGraphicsShapes(t.getWidth(), t.getHeight());
        gn.paint(g2);
        g2.dispose();
        return t;
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

    final static Color grayStColors[] = new Color[] { new Color(255, 255, 255),
            new Color(131, 131, 131), new Color(101, 101, 101),
            new Color(76, 76, 76), new Color(56, 56, 56),
            new Color(14, 14, 14), new Color(249, 249, 249) };

    final static Color blueStColors[] = new Color[] { new Color(255, 255, 255),
            new Color(61, 50, 212), new Color(43, 34, 165),
            new Color(32, 26, 121), new Color(26, 21, 97),
            new Color(10, 8, 36), new Color(207, 204, 244) };

    final static Color redStColors[] = new Color[] { new Color(255, 255, 255),
            new Color(212, 51, 51), new Color(165, 36, 36),
            new Color(120, 26, 26), new Color(102, 22, 22),
            new Color(23, 5, 5), new Color(244, 204, 204) };

    final static Color greenStColors[] = new Color[] {
            new Color(255, 255, 255), new Color(76, 212, 51),
            new Color(56, 165, 36), new Color(41, 120, 26),
            new Color(35, 102, 22), new Color(12, 36, 8),
            new Color(210, 244, 204) };

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

        // steel
        if (l.hasTile(T_STEEL) || l.hasTile(T_RSTEEL) || l.hasTile(T_GSTEEL)
                || l.hasTile(T_BSTEEL)) {
            PdfTemplate steelTemps[] = createSteelTemplates(cb);
            layDownBlockTile(l, cb, T_STEEL, grayStColors, steelTemps);
            layDownBlockTile(l, cb, T_RSTEEL, redStColors, steelTemps);
            layDownBlockTile(l, cb, T_GSTEEL, greenStColors, steelTemps);
            layDownBlockTile(l, cb, T_BSTEEL, blueStColors, steelTemps);
        }

        // simple overlay
        layDownExit(l, cb);
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
        byte onOffTiles[] = new byte[] { T_ON, T_OFF };
        drawSolid(l, cb, onOffTiles, new Color(142, 142, 142));
        layDownTilesByName(l, cb, onOffTiles, "on-off-common.svg");
        layDownSimpleTile(l, cb, T_ON);
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

        drawWire(l, cb);

        // then the things on top

    }

    private static void drawWire(Level l, PdfContentByte cb) {
        // make map of wire terminating points
        int w = l.getWidth();
        int h = l.getHeight();

        boolean map[][] = new boolean[h * 3][w * 3];
        for (int y = 0; y < map.length; y += 3) {
            boolean[] row = map[y];
            for (int x = 0; x < row.length; x += 3) {
                byte t = l.tileAt(x / 3, y / 3);

                switch (t) {
                case T_NS:
                    checkTermination(l, map, y, x, true, true, false, false);
                    break;

                case T_NE:
                    checkTermination(l, map, y, x, true, false, true, false);
                    break;

                case T_NW:
                    checkTermination(l, map, y, x, true, false, false, true);
                    break;

                case T_SE:
                    checkTermination(l, map, y, x, false, true, true, false);
                    break;

                case T_SW:
                    checkTermination(l, map, y, x, false, true, false, true);
                    break;

                case T_WE:
                    checkTermination(l, map, y, x, false, false, true, true);
                    break;

                case T_BUTTON:
                case T_BLIGHT:
                case T_RLIGHT:
                case T_GLIGHT:
                case T_NSWE:
                case T_TRANSPONDER:
                    checkTermination(l, map, y, x, true, true, true, true);
                    break;

                default:
                // nothing
                }
            }
        }

        printMap(map);

        // now, we have wire endpoints, so follow them
        List<List<Point2D>> paths = new ArrayList<List<Point2D>>();
        for (int y = 0; y < map.length; y++) {
            boolean[] row = map[y];
            for (int x = 0; x < row.length; x++) {
                if (!map[y][x]) {
                    continue;
                }
                // mark as taken
                map[y][x] = false;

                // follow
                List<Point2D> path = followWire(l, map, y, x);
                paths.add(path);
            }
        }

        // simplify
        List<List<Point2D>> simplePaths = simplifyPaths(paths);

        System.out.println(paths);

        // draw
        // cb.setColorFill(new Color(47, 47, 47));
        cb.setColorFill(Color.BLUE);
        cb.setLineWidth(5f);
        strokeWire(cb, simplePaths, h);
        cb.setColorFill(Color.RED);
        cb.setLineWidth(3f);
        strokeWire(cb, simplePaths, h);
    }

    private static List<Point2D> followWire(Level l, boolean[][] map, int y,
            int x) {
        List<Point2D> path = new ArrayList<Point2D>();
        // add initial point
        path.add(new Point2D.Float(x, y));

        byte lastDir = Entity.DIR_NONE;
        while (!map[y][x]) {
            // move onto the next tile
            switch (lastDir) {
            case Entity.DIR_UP:
                y--;
                break;

            case Entity.DIR_DOWN:
                y++;
                break;

            case Entity.DIR_LEFT:
                x--;
                break;

            case Entity.DIR_RIGHT:
                x++;
                break;
            }

            // get the new tile
            byte t = l.tileAt(x / 3, y / 3);
            System.out.println("following (" + x + "," + y + "): " + t);

            // follow it
            switch (t) {
            case T_NS:
                if (y % 3 == 0) {
                    // top, moving down
                    y += 2;
                    lastDir = Entity.DIR_DOWN;
                } else {
                    // bottom, moving up
                    y -= 2;
                    lastDir = Entity.DIR_UP;
                }
                path.add(new Point2D.Float(x, y));
                break;

            case T_NE:
                if (y % 3 == 0) {
                    // top, moving down, then right
                    y += 1;
                    path.add(new Point2D.Float(x, y));
                    x += 1;
                    path.add(new Point2D.Float(x, y));
                    lastDir = Entity.DIR_RIGHT;
                } else {
                    // right, moving left, then up
                    x -= 1;
                    path.add(new Point2D.Float(x, y));
                    y -= 2;
                    path.add(new Point2D.Float(x, y));
                    lastDir = Entity.DIR_UP;
                }
                break;

            case T_NW:
                if (y % 3 == 0) {
                    // top, moving down, then left
                    y += 1;
                    path.add(new Point2D.Float(x, y));
                    x -= 1;
                    path.add(new Point2D.Float(x, y));
                    lastDir = Entity.DIR_LEFT;
                } else {
                    // left, moving right, then up
                    x += 1;
                    path.add(new Point2D.Float(x, y));
                    y -= 1;
                    path.add(new Point2D.Float(x, y));
                    lastDir = Entity.DIR_UP;
                }
                break;

            case T_SE:
                if (y % 3 == 2) {
                    // bottom, moving up, then right
                    y -= 1;
                    path.add(new Point2D.Float(x, y));
                    x += 1;
                    path.add(new Point2D.Float(x, y));
                    lastDir = Entity.DIR_RIGHT;
                } else {
                    // right, moving left, then down
                    x -= 1;
                    path.add(new Point2D.Float(x, y));
                    y += 1;
                    path.add(new Point2D.Float(x, y));
                    lastDir = Entity.DIR_DOWN;
                }
                break;

            case T_SW:
                if (y % 3 == 0) {
                    // bottom, moving up, then left
                    y -= 1;
                    path.add(new Point2D.Float(x, y));
                    x -= 1;
                    path.add(new Point2D.Float(x, y));
                    lastDir = Entity.DIR_LEFT;
                } else {
                    // left, moving right, then down
                    x += 1;
                    path.add(new Point2D.Float(x, y));
                    y += 1;
                    path.add(new Point2D.Float(x, y));
                    lastDir = Entity.DIR_DOWN;
                }
                break;

            case T_WE:
                if (x % 3 == 0) {
                    // left, moving right
                    x += 2;
                    lastDir = Entity.DIR_RIGHT;
                } else {
                    // right, moving left
                    x -= 2;
                    lastDir = Entity.DIR_LEFT;
                }
                path.add(new Point2D.Float(x, y));
                break;

            case T_BUTTON:
            case T_BLIGHT:
            case T_RLIGHT:
            case T_GLIGHT:
            case T_NSWE:
            case T_TRANSPONDER:
                if (x % 3 == 0) {
                    // left, moving right
                    x += 2;
                    lastDir = Entity.DIR_RIGHT;
                } else if (y % 3 == 0) {
                    // top, moving down
                    y += 2;
                    lastDir = Entity.DIR_DOWN;
                } else if (x % 3 == 2) {
                    // right, moving left
                    x -= 2;
                    lastDir = Entity.DIR_LEFT;
                } else {
                    // bottom, moving up
                    y -= 2;
                    lastDir = Entity.DIR_UP;
                }
                path.add(new Point2D.Float(x, y));
                break;

            default:
                assert false : "Should have wire tile";
            }
        }
        map[y][x] = false;

        return path;
    }

    private static void checkTermination(Level l, boolean[][] map, int y,
            int x, boolean north, boolean south, boolean east, boolean west) {
        if (north) {
            byte t = getTile(l, y / 3 - 1, x / 3);
            if (!wireConnects(t, Entity.DIR_DOWN)) {
                setNorth(map, y, x);
            }
        }
        if (south) {
            byte t = getTile(l, y / 3 + 1, x / 3);
            if (!wireConnects(t, Entity.DIR_UP)) {
                setSouth(map, y, x);
            }
        }
        if (east) {
            byte t = getTile(l, y / 3, x / 3 + 1);
            if (!wireConnects(t, Entity.DIR_LEFT)) {
                setEast(map, y, x);
            }
        }
        if (west) {
            byte t = getTile(l, y / 3, x / 3 - 1);
            if (!wireConnects(t, Entity.DIR_RIGHT)) {
                setWest(map, y, x);
            }
        }
    }

    private static boolean wireConnects(byte t, byte dir) {
        switch (t) {
        case T_NS:
            return dir == Entity.DIR_UP || dir == Entity.DIR_DOWN;

        case T_NE:
            return dir == Entity.DIR_UP || dir == Entity.DIR_RIGHT;

        case T_NW:
            return dir == Entity.DIR_UP || dir == Entity.DIR_LEFT;

        case T_SE:
            return dir == Entity.DIR_DOWN || dir == Entity.DIR_RIGHT;

        case T_SW:
            return dir == Entity.DIR_DOWN || dir == Entity.DIR_LEFT;

        case T_WE:
            return dir == Entity.DIR_LEFT || dir == Entity.DIR_RIGHT;

        case T_BUTTON:
        case T_BLIGHT:
        case T_RLIGHT:
        case T_GLIGHT:
        case T_NSWE:
        case T_TRANSPONDER:
            return true;

        default:
            return false;
        }
    }

    private static byte getTile(Level l, int y, int x) {
        if (y < 0 || x < 0 || y >= l.getHeight() || x >= l.getWidth()) {
            return -1;
        }
        return l.tileAt(x, y);
    }

    private static void strokeWire(PdfContentByte cb,
            List<List<Point2D>> paths, int h) {
        for (List<Point2D> path : paths) {
            System.out.println("simple path: " + path);

            Point2D p = transformForWire(h, path.get(0));
            cb.moveTo((float) p.getX(), (float) p.getY());
            for (int i = 1; i < path.size(); i++) {
                p = path.get(i);
                double x = p.getX();
                double y = p.getY();

                Point2D tP = transformForWire(h, x, y);
                cb.lineTo((float) tP.getX(), (float) tP.getY());
            }
        }
        cb.stroke();
    }

    private static Point2D transformForWire(int h, Point2D p) {
        return transformForWire(h, p.getX(), p.getY());
    }

    private static Point2D transformForWire(int h, double x, double y) {
        Point2D tP = new Point2D.Double(x * BASE_TILE_SIZE / 3, (h * 3 - y)
                * BASE_TILE_SIZE / 3);
        return tP;
    }

    private static List<List<Point2D>> shrinkPathsForWire(
            List<List<Point2D>> paths, float s) {
        List<List<Point2D>> result = new ArrayList<List<Point2D>>();
        for (List<Point2D> path : paths) {
            List<Point2D> rPath = new ArrayList<Point2D>();
            result.add(rPath);

            for (Point2D p : path) {
                double x = p.getX();
                double y = p.getY();

                int xm = (int) (Math.floor(x) % 3);
                int ym = (int) (Math.floor(y) % 3);
                switch (xm) {
                case 1:
                    x += s;
                    break;
                case 2:
                    x -= s;
                    break;
                }

                switch (ym) {
                case 1:
                    y += s;
                    break;
                case 2:
                    y -= s;
                    break;
                }

                rPath.add(new Point2D.Double(x, y));
            }
        }

        return result;
    }

    private static void setWest(boolean[][] map, int y, int x) {
        map[y + 1][x] = true;
    }

    private static void setEast(boolean[][] map, int y, int x) {
        map[y + 1][x + 2] = true;
    }

    private static void setSouth(boolean[][] map, int y, int x) {
        map[y + 2][x + 1] = true;
    }

    private static void setNorth(boolean[][] map, int y, int x) {
        map[y][x + 1] = true;
    }

    private static PdfTemplate[] createSteelTemplates(PdfContentByte cb) {
        try {
            SVGDocument doc = loadSVG("steel-pieces.svg");

            PdfTemplate temps[] = new PdfTemplate[6];

            createSteelOrBlockTemplates(cb, doc, temps);

            return temps;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void createSteelOrBlockTemplates(PdfContentByte cb,
            SVGDocument doc, PdfTemplate[] temps) {
        BridgeContext bc = new BridgeContext(new UserAgentAdapter());
        GVTBuilder gvtb = new GVTBuilder();

        GraphicsNode gn = gvtb.build(bc, doc);
        GVTTreeWalker tw = new GVTTreeWalker(gn);
        gn = tw.nextGraphicsNode();
        for (int i = 0; i < temps.length; i++) {
            PdfTemplate t = cb.createTemplate(BASE_TILE_SIZE, BASE_TILE_SIZE);
            temps[i] = t;

            // System.out.println("node " + i);
            gn = tw.nextGraphicsNode();

            readAndStrokeBlocks(t, gn);
        }
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
        boolean whereToDraw[][] = makeTileMap(l, TileCondition
                .createTileMatchCondition(tile));

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
        boolean whereToDraw[][] = makeTileMap(l, TileCondition
                .createTileMatchCondition(tile));

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
        boolean whereToDraw[][] = makeTileMap(l, TileCondition
                .createTileMatchCondition(tile));

        // draw each tile
        drawTiles(l, cb, tileTemplate, whereToDraw);

        cb.restoreState();
    }

    private static PdfTemplate[] createSphereTemplates(PdfContentByte cb) {
        try {
            SVGDocument doc = loadSVG("sphere-pieces.svg");

            PdfTemplate temps[] = new PdfTemplate[6];

            createSteelOrBlockTemplates(cb, doc, temps);

            return temps;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void layDownTilesByName(Level l, PdfContentByte cb,
            byte tiles[], String name) {

        // check
        boolean hasTiles = false;
        for (int i = 0; i < tiles.length; i++) {
            byte t = tiles[i];
            if (l.hasTile(t)) {
                hasTiles = true;
                break;
            }
        }
        if (!hasTiles) {
            return;
        }

        PdfTemplate tileTemplate = createTileTemplate(cb, name);
        boolean whereToDraw[][] = makeTileMap(l, TileCondition
                .createTileMatchCondition(tiles));

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
        boolean whereToDraw[][] = makeTileMap(l, TileCondition
                .createTileMatchCondition(tile));

        drawTiles(l, cb, tileTemplate, whereToDraw);
    }

    private static void layDownExit(Level l, PdfContentByte cb) {
        if (!l.hasTile(T_EXIT)) {
            return;
        }

        // 2 different things, depending on entity/no entity
        byte tile = T_EXIT;

        boolean whereToDraw[][] = makeTileMap(l, TileCondition
                .createTileMatchCondition(tile, false));
        boolean whereToDraw2[][] = makeTileMap(l, TileCondition
                .createTileMatchCondition(tile, true));

        boolean nonEntExit = false;
        boolean entExit = false;
        for (int y = 0; y < whereToDraw.length; y++) {
            boolean row[] = whereToDraw[y];
            boolean row2[] = whereToDraw2[y];
            for (int x = 0; x < row.length; x++) {
                if (row[x]) {
                    nonEntExit = true;
                }
                if (row2[x]) {
                    entExit = true;
                }
                if (nonEntExit && entExit) {
                    break;
                }
            }
        }

        if (nonEntExit) {
            PdfTemplate tileTemplate = createTileTemplate(cb, T_EXIT);
            drawTiles(l, cb, tileTemplate, whereToDraw);
        }
        if (entExit) {
            PdfTemplate tileTemplate = createTileTemplate(cb,
                    "animation/door_opens2.svg");
            drawTiles(l, cb, tileTemplate, whereToDraw2);
        }
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

    private static PdfTemplate[] createBlockTemplates(PdfContentByte cb) {
        try {
            SVGDocument doc = loadSVG("block-pieces.svg");

            PdfTemplate temps[] = new PdfTemplate[4];

            createSteelOrBlockTemplates(cb, doc, temps);

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

    private static boolean[][] makePathsFromTile(Level l, PdfContentByte cb,
            byte tiles[]) {

        boolean map[][] = makeTileMap(l, TileCondition
                .createTileMatchCondition(tiles));
        // printMap(map);

        byte[][] edges = findEdges(map);

        System.out.println();
        printMap(edges, map);

        java.util.List<List<Point2D>> paths = makePathsFromEdges(edges);

        List<List<Point2D>> simplePaths = simplifyPaths(paths);

        int h = l.getHeight();
        for (List<Point2D> path : simplePaths) {
            System.out.println("simple path: " + path);

            Iterator i = path.iterator();
            Point2D p = (Point2D) i.next();
            cb.moveTo((float) p.getX() * BASE_TILE_SIZE, (float) (h - p.getY())
                    * BASE_TILE_SIZE);
            while (i.hasNext()) {
                p = (Point2D) i.next();
                cb.lineTo((float) p.getX() * BASE_TILE_SIZE, (float) (h - p
                        .getY())
                        * BASE_TILE_SIZE);
            }
            cb.closePath();
        }
        return map;
    }

    private static List<List<Point2D>> simplifyPaths(
            java.util.List<List<Point2D>> paths) {
        List<List<Point2D>> simplePaths = new ArrayList<List<Point2D>>();
        // now, we have the segments, so get it down to corners
        for (Iterator<List<Point2D>> iter = paths.iterator(); iter.hasNext();) {
            List path = iter.next();
            List<Point2D> newPath = new ArrayList<Point2D>();
            simplePaths.add(newPath);

            Iterator iter2 = path.iterator();
            Point2D prev;
            Point2D current = (Point2D) iter2.next();
            newPath.add(current);

            byte oldDir = DIR_NONE;
            while (iter2.hasNext()) {
                prev = current;
                current = (Point2D) iter2.next();

                double x = current.getX();
                double y = current.getY();
                double px = prev.getX();
                double py = prev.getY();

                double dx = x - px;
                double dy = y - py;

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
        return simplePaths;
    }

    private static java.util.List<List<Point2D>> makePathsFromEdges(
            byte[][] edges) {
        // make the paths list
        java.util.List<List<Point2D>> paths = new ArrayList<List<Point2D>>();

        Point start = new Point(0, 0);
        while ((start = getNextStart(edges, start)) != null) {
            // new path
            java.util.List<Point2D> path = new ArrayList<Point2D>();
            paths.add(path);

            path.add(new Point2D.Double(start.x, start.y));

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
                path.add(new Point2D.Double(x, y));
            }
        }
        return paths;
    }

    private static byte[][] findEdges(boolean[][] map) {
        // zip across and find all the edges, jcreed-style!
        byte[][] edges = new byte[map.length + 1][map[0].length + 1];
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
        return edges;
    }

    private static Point getNextStart(byte[][] edges, Point start) {
        int startX = start.x;
        for (int y = start.y; y < edges.length; y++) {
            byte[] row = edges[y];
            if (y != start.y) {
                startX = 0;
            }
            for (int x = startX; x < row.length; x++) {
                if (row[x] != DIR_NONE) {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    static void printMap(boolean[][] map) {
        for (int y = 0; y < map.length; y++) {
            boolean[] row = map[y];
            for (int x = 0; x < row.length; x++) {
                System.out.print(row[x] ? "X " : ". ");
            }
            System.out.println();
        }
    }

    static void printMap(byte[][] edges, boolean[][] map) {
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
                    System.out.print(map[y][x] ? " X" : " .");
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

    private static boolean[][] makeTileMap(Level l, TileCondition c) {
        int w = l.getWidth();
        int h = l.getHeight();

        boolean space[][] = new boolean[h][w];
        for (int y = 0; y < space.length; y++) {
            boolean[] row = space[y];
            for (int x = 0; x < row.length; x++) {
                if (c.test(l, x, y)) {
                    row[x] = true;
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
        cb.setColorFill(Color.BLACK);
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
