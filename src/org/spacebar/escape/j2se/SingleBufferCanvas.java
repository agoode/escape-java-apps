/*
 * Created on Apr 4, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.spacebar.escape.j2se;

import java.awt.Graphics;
import java.awt.Graphics2D;

import org.spacebar.escape.common.Continuation;

/**
 * @author adam
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
abstract public class SingleBufferCanvas extends BufferCanvas {

    public SingleBufferCanvas(Continuation c) {
        super(c);
//        setDoubleBuffered(true);
    }

    public SingleBufferCanvas() {
        super(null);
//        setDoubleBuffered(true);
    }

    abstract protected void bufferPaint(Graphics2D g);

    protected void bufferRepaint() {
        repaint();
    }

    protected void paintComponent(Graphics g) {
        bufferPaint((Graphics2D) g);
    }
}
