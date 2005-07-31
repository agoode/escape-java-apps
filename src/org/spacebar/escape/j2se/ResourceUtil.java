/*
 * Created on Dec 20, 2004
 *
 */
package org.spacebar.escape.j2se;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;

/**
 * @author adam
 */
public class ResourceUtil {
    //    private final static Mixer mixer = AudioSystem.getMixer(null);

    public static InputStream getLocalResourceAsStream(String name) {
        return ResourceUtil.class.getResourceAsStream("resources/" + name);
    }

    public static Clip loadClip(String name) {
        System.out.print("loading " + name + "...");
        System.out.flush();

        Clip clip = null;
        try {
            AudioInputStream a = loadAudio(name);
            DataLine.Info dlInfo = new DataLine.Info(Clip.class, a.getFormat(),
                    10);

            //            clip = (Clip) mixer.getLine(dlInfo);
            clip = (Clip) AudioSystem.getLine(dlInfo);

            System.out.print(" " + clip.getLineInfo() + "...");
            System.out.flush();
            clip.open(a);

            System.out.println(" success!");
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return clip;
    }

    public static AudioInputStream loadAudio(String name) {
        InputStream in = getLocalResourceAsStream(name);
        AudioInputStream a = null;
        try {
            a = AudioSystem.getAudioInputStream(in);
        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
        }
        return a;
    }

    public static BufferedImage[] loadScaledImages(String name, int smaller,
            int bigger) {
        final BufferedImage origImg = loadImage(name);

        return createScaledImages(origImg, smaller, bigger);
    }

    /**
     * @param original
     * @param smaller
     * @param bigger
     * @return
     */
    static BufferedImage[] createScaledImages(BufferedImage original,
            int smaller, int bigger) {
        BufferedImage smallerImgs[] = new BufferedImage[smaller + 1];
        smallerImgs[0] = original;
        for (int i = 1; i < smaller + 1; i++) {
            final BufferedImage img = smallerImgs[i - 1];
            int w = img.getWidth() >> 1;
            int h = img.getHeight() >> 1;

            smallerImgs[i] = createCompatibleImage(w, h, img.getColorModel()
                    .getTransparency());

            Graphics2D g = smallerImgs[i].createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.scale(0.5, 0.5);
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }

        BufferedImage biggerImgs[] = new BufferedImage[bigger];
        for (int i = 0; i < bigger; i++) {
            int scale = i + 1;

            int w = original.getWidth() << scale;
            int h = original.getHeight() << scale;

            biggerImgs[i] = createCompatibleImage(w, h, original
                    .getColorModel().getTransparency());

            Graphics2D g = biggerImgs[i].createGraphics();
            g.scale(1 << scale, 1 << scale);
            g.drawImage(original, 0, 0, null);
            g.dispose();
        }

        final BufferedImage imgs[] = new BufferedImage[smaller + bigger + 1];
        System.arraycopy(smallerImgs, 0, imgs, 0, smaller);
        System.arraycopy(biggerImgs, 0, imgs, smaller, bigger);

        for (int i = 0; i < imgs.length; i++) {
            //            System.out.println("w: " + imgs[i].getWidth() + ", h: "
            //                    + imgs[i].getHeight());
        }

        return imgs;
    }

    public static BufferedImage loadImage(String name) {
        InputStream in = ResourceUtil.getLocalResourceAsStream(name);
        BufferedImage img = null;
        try {
            img = ImageIO.read(in);
        } catch (IOException e) {
            e.printStackTrace();
        }

        BufferedImage img2 = createCompatibleImage(img.getWidth(), img
                .getHeight(), img.getColorModel().getTransparency());

        Graphics2D g = img2.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        //        System.out.println(img2);

        return img2;
    }

    public static BufferedImage stitchHoriz(String names[]) {
        BufferedImage imgs[] = new BufferedImage[names.length];
        for (int i = 0; i < imgs.length; i++) {
            imgs[i] = loadImage(names[i]);
        }
        return stitchHoriz(imgs);
    }
    
    public static BufferedImage stitchHoriz(BufferedImage imgs[]) {
        int height = 0;
        int maxWidth = 0;

        // get max height and width and transparency
        for (int i = 0; i < imgs.length; i++) {
            height = Math.max(height, imgs[i].getHeight());
            maxWidth = Math.max(maxWidth, imgs[i].getWidth());
        }

        BufferedImage newImg = createCompatibleImage(maxWidth * imgs.length, height,
                Transparency.TRANSLUCENT);
//        System.out.println(newImg);
        Graphics2D g2 = newImg.createGraphics();
        int dx = 0;
        for (int i = 0; i < imgs.length; i++) {
            // bottom aligned
            int h = imgs[i].getHeight();
            int dy = height - h;

            g2.drawImage(imgs[i], dx, dy, null);
            dx += 32; // XXX
        }
        g2.dispose();
        return newImg;
    }

    private static BufferedImage createCompatibleImage(int width, int height,
            int transparency) {
        GraphicsEnvironment ge = GraphicsEnvironment
                .getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        BufferedImage img = gc.createCompatibleImage(width, height,
                transparency);
        return img;
    }
}