/*
 * Created on Dec 27, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.spacebar.escape.j2se;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;

import javax.swing.*;

import org.spacebar.escape.common.Characters;

/**
 * @author adam
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class Message implements Overlay {

    private static final String defaultIcon = Characters.PICS
            + Characters.EXCICON + Characters.POP;

    static public void bug(BufferCanvas dbc, String title) {
        String ok = Characters.RED + "I'll file a bug report!" + Characters.POP;
        String t = Characters.PICS + Characters.BUGICON + Characters.POP
                + Characters.RED + " BUG: " + Characters.POP + Characters.WHITE
                + title;
        Message m = new Message(dbc, t, 0, true, ok, null);
        m.ask();
    }

    static public void no(BufferCanvas dbc, String title) {
        String t = Characters.PICS + Characters.XICON + Characters.POP
                + Characters.WHITE + " " + title;
        Message m = new Message(dbc, t, 0, true, "OK", null);
        m.ask();
    }

    // set position, with negative being from bottom of screen
    static public boolean quick(BufferCanvas dbc, String title, int yPos,
            String ok, String cancel) {
        return quick(dbc, title, yPos, ok, cancel, defaultIcon);
    }

    static public boolean quick(BufferCanvas dbc, String title, int yPos,
            String ok, String cancel, String icon) {

        String t = icon + Characters.WHITE + " " + title;
        Message m = new Message(dbc, t, yPos, false, ok, cancel);
        return m.ask();
    }

    // auto position
    static public boolean quick(BufferCanvas dbc, String title,
            String ok, String cancel) {
        return quick(dbc, title, ok, cancel, defaultIcon);
    }

    static public boolean quick(BufferCanvas dbc, String title,
            String ok, String cancel, String icon) {
        String t = icon + Characters.WHITE + " " + title;
        Message m = new Message(dbc, t, 0, true, ok, cancel);
        return m.ask();
    }

    final private String cancel;

    final private BufferCanvas dbc;

    final private String ok;

    final private String title;

    final private int yPos;

    final private boolean autoYPos;

    boolean result;

    private Message(BufferCanvas dbc, String title, int yPos,
            boolean autoYPos, String ok, String cancel) {
        this.dbc = dbc;
        this.title = title;
        this.yPos = yPos;
        this.autoYPos = autoYPos;
        this.ok = ok;
        this.cancel = cancel;
    }

    synchronized private boolean ask() {
        // save
        InputMap oldIMap = dbc.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap oldAMap = dbc.getActionMap();
        Overlay o = dbc.getOverlay();

        // set our maps
        ComponentInputMap im = new ComponentInputMap(dbc);
        im.put(KeyStroke.getKeyStroke("ENTER"), "ok");
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        dbc.setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, im);
        ActionMap am = makeActionMap();
        dbc.setActionMap(am);

        // set overlay
        dbc.setOverlay(this);

        // wait...
        try {
            wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // magically, the result will be set

        // restore
        dbc.setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, oldIMap);
        dbc.setActionMap(oldAMap);
        dbc.setOverlay(o);

        // return
        return result;
    }

    private ActionMap makeActionMap() {
        ActionMap am = new ActionMap();
        am.put("ok", new Asker(true));
        am.put("cancel", new Asker(false));

        return am;
    }

    private class Asker extends AbstractAction {
        final boolean action;

        Asker(boolean action) {
            this.action = action;
        }

        public void actionPerformed(ActionEvent e) {
            synchronized (Message.this) {
                result = action;
                Message.this.notify();
            }
        }
    }

    public void draw(Graphics2D g, int width, int height) {
        AffineTransform at = g.getTransform();

        // find longest line
        int ll = 0;

        String titlen = title + "\n";
        int cl = 0;
        for (int i = 0; i < titlen.length(); i++) {
            if (titlen.charAt(i) == '\n') {
                ll = Math.max(Characters.width(titlen.substring(cl, i - cl)),
                        ll);
                cl = i;
            } else if (titlen.charAt(cl) == '\n') {
                cl = i;
            }
        }

        int w = (2 * Characters.FONT_ADVANCE)
                + Math.max(ll, Characters.width("ESCAPE: ")
                        + Math.max(Characters.width(ok), Characters
                                .width(cancel)));

        int nlines = Characters.lines(title);

        int h;
        if (cancel == null) {
            h = ((3 + nlines) * Characters.FONT_HEIGHT);
        } else {
            h = ((4 + nlines) * Characters.FONT_HEIGHT);
        }

        // now center
        int x = (dbc.getWidth() - w) / 2;
        int y;

        // and y, if desired
        if (autoYPos) {
            y = (dbc.getHeight() - h) / 2;
        } else if (yPos < 0) {
            y = dbc.getHeight() + yPos;
        } else {
            y = yPos;
        }

        g.translate(x, y);

        // background
        Rectangle r = new Rectangle(2, 2, w - 4, h - 4);
        g.setColor(new Color(90, 90, 90, 200));
        g.fill(r);

        r.setBounds(1, 1, w - 2, h - 2);
        g.setColor(new Color(36, 36, 36, 200));
        g.setStroke(new BasicStroke(2));
        g.draw(r);

        // text
        g.translate(Characters.FONT_ADVANCE, Characters.FONT_HEIGHT);
        Drawing.drawString(g, title);
        g.translate(0, nlines * Characters.FONT_HEIGHT);
        Drawing.drawString(g, Characters.YELLOW + "ENTER" + Characters.POP
                + ":  " + ok);
        if (cancel != null) {
            g.translate(0, Characters.FONT_HEIGHT);
            Drawing.drawString(g, Characters.YELLOW + "ESCAPE"
                    + Characters.POP + ": " + cancel);
        }

        g.setTransform(at);
    }
}