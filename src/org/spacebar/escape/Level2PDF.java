package org.spacebar.escape;

import java.awt.Color;
import java.io.*;
import java.util.regex.Pattern;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Characters;
import org.spacebar.escape.common.Level;
import org.spacebar.escape.common.StyleStack;
import org.spacebar.escape.j2se.ResourceUtil;
import org.spacebar.escape.j2se.StyleStack2;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

public class Level2PDF {
    final public static String id = "$Id$";
    final public static String creator = id.replaceAll("^\\$Id: (.*)\\$$", "$1");
        
    
    final public static void main(String[] args) {
        try {
            // get level
            File f = new File(args[0]);
            Level l = new Level(new BitInputStream(new FileInputStream(f)));
            l.print(System.out);

            String basename = f.getName().replaceFirst("\\.esx$", "");
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
                InputStream font = ResourceUtil
                        .getLocalResourceAsStream(fontName);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int d;
                while ((d = font.read()) != -1) {
                    baos.write(d);
                }
                fontData = baos.toByteArray();
            }

            Rectangle page = PageSize.LETTER;
            if (landscape && page.height() > page.width()) {
                page = page.rotate();
            }
            
            Document document = new Document(page, margin, margin,
                    margin, margin);
            
            try {
                // we create a writer that listens to the document
                // and directs a PDF-stream to a file
                PdfWriter.getInstance(document, new FileOutputStream(basename
                        + ".pdf"));

                // metadata
                document.addAuthor(StyleStack.removeStyle(l.getAuthor()));
                document.addTitle(StyleStack.removeStyle(l.getTitle()));
                document.addCreationDate();
                document.addProducer();
                document.addCreator(creator);
                document.addSubject("Escape");

                document.open();

                BaseFont fixedsys = BaseFont.createFont(fontName,
                        BaseFont.CP1252, true, true, fontData, null);
                Font font = new Font(fixedsys, 16);

                String text = Characters.WHITE + l.getTitle() + Characters.GRAY
                        + " by " + Characters.BLUE + l.getAuthor();

                PdfPTable t = new PdfPTable(1);
                t.setWidthPercentage(100);
                PdfPCell cell = new PdfPCell(new Paragraph(formatText(text,
                        font)));
                cell.setBackgroundColor(new Color(34, 34, 68));
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
                    tileSize = rWidth / (l.getWidth() + 0.5f); // add 0.5 for padding
                } else {
                    tileSize = rHeight / (l.getHeight() + 0.5f);
                }
                float padding = tileSize / 4;
                w = tileSize * l.getWidth();
                h = tileSize * l.getHeight();
                
                // one of the next two should be 0
                xOff = (rWidth - (w + 2 * padding)) / 2;
                yOff = (rHeight - (h + 2 * padding)) / 2;
                System.out.println("xOff: " + xOff + ", yOff: " + yOff);
                
                Rectangle bg = new Rectangle(xOff + margin, yOff + margin, xOff + w + margin
                        + padding * 2, yOff + h + margin + padding * 2);
                bg.setBackgroundColor(Color.BLACK);
                document.add(bg);

                Rectangle inner = new Rectangle(xOff + margin + padding, yOff + margin
                        + padding, xOff + w + margin + padding, yOff + h + margin
                        + padding);
                inner.setBackgroundColor(new Color(195, 195, 195));
                document.add(inner);

                document.close();
            } catch (DocumentException de) {
                System.err.println(de.getMessage());
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
            }

            // done
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
