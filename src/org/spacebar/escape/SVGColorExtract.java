package org.spacebar.escape;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.gvt.GVTTreeWalker;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.svg.SVGDocument;

public class SVGColorExtract {
    final static String parser = XMLResourceDescriptor.getXMLParserClassName();

    final static SAXSVGDocumentFactory svgDocFactory = new SAXSVGDocumentFactory(
            parser);

    public static void main(String[] args) {
        try {
            FileInputStream f = new FileInputStream(args[0]);

            SVGDocument doc = (SVGDocument) svgDocFactory.createDocument(null,
                    f);

            BridgeContext bc = new BridgeContext(new UserAgentAdapter());
            GVTBuilder gvtb = new GVTBuilder();

            GraphicsNode gn = gvtb.build(bc, doc);
            GVTTreeWalker tw = new GVTTreeWalker(gn);
            gn = tw.nextGraphicsNode();

            System.out.print("new Color[] { ");

            Graphics2D g2 = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .createGraphics(
                            new BufferedImage(32, 32,
                                    BufferedImage.TYPE_3BYTE_BGR));

            while(true) {
                // System.out.println("node " + i);
                gn = tw.nextGraphicsNode();
                if (gn == null) {
                    break;
                }
                
                gn.paint(g2);
                Color c = g2.getColor();
                System.out.print("new Color(" + c.getRed() + ", "
                        + c.getGreen() + ", " + c.getBlue() + "), ");
            }

            System.out.println("}");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
