package org.spacebar.escape;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.*;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Characters;
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

    static {
        ByteBuffer.HIGH_PRECISION = true;
    }

    final public static void main(String[] args) {
        try {
            // get level
            File f = new File(args[0]);
            Level l = new Level(new BitInputStream(new FileInputStream(f)));
            l.print(System.out);

            String basename = f.getName().replaceFirst("\\.esx$", "");

            makePDF(l, PageSize._11X17, new FileOutputStream(basename + ".pdf"));
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
    public static void makePDF(Level l, Rectangle page, OutputStream out)
            throws IOException {
        float levAspect = (float) l.getWidth() / (float) l.getHeight();

        // do PDF stuff
        int margin = 18;

        // XXX: take into account title block?
        boolean landscape = levAspect >= 1.0;
        System.out.println("landscape: " + landscape);

        // get the font
        String fontName = "Fixedsys500c.ttf";
        byte fontData[];
        {
            InputStream font = ResourceUtil.getLocalResourceAsStream(fontName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int d;
            while ((d = font.read()) != -1) {
                baos.write(d);
            }
            fontData = baos.toByteArray();
        }

        if (landscape && page.height() > page.width()) {
            page = page.rotate();
        }

        Document.compress = false;
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
            BaseFont fixedsys = BaseFont.createFont(fontName, BaseFont.CP1252,
                    true, true, fontData, null);
            Font font = new Font(fixedsys, 16);

            String text = Characters.WHITE + l.getTitle() + Characters.GRAY
                    + " by " + Characters.BLUE + l.getAuthor();

            PdfPTable t = new PdfPTable(1);
            t.setWidthPercentage(100);
            PdfPCell cell = new PdfPCell(new Paragraph(formatText(text, font)));
            Color bannerC = new Color(34, 34, 68);
            cell.setBackgroundColor(bannerC);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
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

            PdfTemplate tiles[] = makeTileTemplates(l);

            // transform to fit into page
            AffineTransform af = new AffineTransform();
            af.translate(margin + xOff, margin + yOff);
            af.scale(masterScale, masterScale);
            cb.transform(af);

            layDownBlack(l, cb);
            cb.restoreState();

            // create the level as a form XObject, so that patterns come out
            // nice
            PdfTemplate levelField = cb.createTemplate(l.getWidth() * BASE_TILE_SIZE,
                    l.getHeight() * BASE_TILE_SIZE);
            levelField.saveState();
            PdfPatternPainter brickPattern = createBrickPattern(levelField);
            layDownBrick(l, levelField, brickPattern);

            levelField.restoreState();
            levelField.saveState();
            layDownRough(l, levelField);

            levelField.restoreState();
            levelField.saveState();
            layDownTiles(l, levelField);

            levelField.restoreState();
            levelField.saveState();
            layDownSprites(l, levelField);

            // hit it
            af = new AffineTransform();
            af.translate(margin + padding + xOff, margin + padding + yOff);
            af.scale(masterScale, masterScale);
            double mat[] = new double[6];
            af.getMatrix(mat);
            cb.addTemplate(levelField, (float) mat[0], (float) mat[1], (float) mat[2],
                    (float) mat[3], (float) mat[4], (float) mat[5]);

            document.close();
        } catch (DocumentException de) {
            System.err.println(de.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }

        // done
    }

    private static PdfTemplate[] makeTileTemplates(Level l) {
        // make bit mask of tiles in this level
        boolean t[] = new boolean[59];
        for (int i = 0; i < l.getWidth() * l.getHeight(); i++) {
            t[l.tileAt(i)] = true;
        }

        // floor is always required (exit)

        // now, determine some classes of tiles to include

        PdfTemplate p[] = new PdfTemplate[59];

        return p;
    }

    private static void layDownSprites(Level l, PdfContentByte cb) {
        // TODO Auto-generated method stub

    }

    private static void layDownTiles(Level l, PdfContentByte cb) {
        // TODO Auto-generated method stub

    }

    private static void layDownRough(Level l, PdfContentByte cb) {
        // TODO Auto-generated method stub

    }

    private static void layDownBrick(Level l, PdfContentByte cb,
            PdfPatternPainter pat) {
        cb.setColorFill(new Color(195, 195, 195));
        cb.rectangle(0, 0, l.getWidth() * BASE_TILE_SIZE, l.getHeight()
                * BASE_TILE_SIZE);
        cb.fill();

        cb.rectangle(0, 0, l.getWidth() * BASE_TILE_SIZE, l.getHeight()
                * BASE_TILE_SIZE);
        cb.setPatternFill(pat);
        cb.fill();
    }

    private static PdfPatternPainter createBrickPattern(PdfContentByte cb) {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
        try {
            SVGDocument doc = (SVGDocument) f.createDocument(null, ResourceUtil
                    .getLocalResourceAsStream("brick-pieces.svg"));
            GVTBuilder gvtb = new GVTBuilder();
            UserAgent ua = new UserAgentAdapter();
            GraphicsNode gn = gvtb.build(new BridgeContext(ua), doc);
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
