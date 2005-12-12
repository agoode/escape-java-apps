package org.spacebar.escape;

import java.awt.Color;
import java.io.*;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Characters;
import org.spacebar.escape.common.Level;
import org.spacebar.escape.j2se.ResourceUtil;
import org.spacebar.escape.j2se.StyleStack2;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;

public class Level2PDF {
    public static void main(String[] args) {
        try {
            // get level
            File f = new File(args[0]);
            Level l = new Level(new BitInputStream(new FileInputStream(f)));
            l.print(System.out);

            String basename = f.getName().replaceFirst("\\.esx$", "");

            // read in font

            // read in SVG fragments

            // do PDF stuff
            Document document = new Document();
            try {
                // we create a writer that listens to the document
                // and directs a PDF-stream to a file
                PdfWriter.getInstance(document, new FileOutputStream(basename
                        + ".pdf"));

                document.open();

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

                BaseFont fixedsys = BaseFont.createFont(fontName,
                        BaseFont.CP1252, true, true, fontData, null);
                Font font = new Font(fixedsys, 14);
                
                String text = Characters.WHITE + l.getTitle() + Characters.GRAY
                        + " by " + Characters.BLUE + l.getAuthor();


                document.add(new Paragraph(formatText(text, font)));
            } catch (DocumentException de) {
                System.err.println(de.getMessage());
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
            }

            // step 5: we close the document
            document.close();

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

    private static void addToPhrase(Font f, StyleStack2 s, Phrase p, StringBuilder str) {
        if (str.length() != 0) {
            System.out.println("making new chunk: " + str);
            Font f2 = new Font(f);
            f2.setColor(s.getAWTColor());
            Chunk c = new Chunk(str.toString(), f2);
            c.setTextRenderMode(PdfContentByte.TEXT_RENDER_MODE_FILL_STROKE, 0, Color.BLACK);
            str.delete(0, str.length());
            p.add(c);
        }
    }
}
