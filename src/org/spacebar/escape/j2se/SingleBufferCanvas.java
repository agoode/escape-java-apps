/*
 * Created on Apr 4, 2005
 */
package org.spacebar.escape.j2se;

import java.awt.Graphics;
import java.awt.Graphics2D;

import org.spacebar.escape.common.Continuation;

/**
 * @author adam
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

    @Override
	abstract protected void bufferPaint(Graphics2D g);

    @Override
	protected void bufferRepaint() {
        repaint();
    }

    @Override
	protected void paintComponent(Graphics g) {
        bufferPaint((Graphics2D) g);
    }
}
