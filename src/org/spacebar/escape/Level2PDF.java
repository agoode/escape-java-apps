package org.spacebar.escape;

import java.io.*;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Level;
import org.spacebar.escape.j2se.ResourceUtil;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
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

                BaseFont fixedsys = BaseFont.createFont(fontName, BaseFont.CP1252, true, true, fontData, null);
                Font font = new Font(fixedsys, 14);

                document.add(new Paragraph(l.getTitle() + " by "
                        + l.getAuthor(), font));
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
}
