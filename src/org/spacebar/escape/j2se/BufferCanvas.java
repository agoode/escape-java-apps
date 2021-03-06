/*
 * Created on Apr 4, 2005
 */
package org.spacebar.escape.j2se;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.VolatileImage;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.spacebar.escape.common.Continuation;

/**
 * @author adam
 */
public abstract class BufferCanvas extends JComponent {

    protected VolatileImage backBuffer;
    protected Overlay overlay;
    final protected Continuation theWayOut;

    protected abstract void bufferPaint(Graphics2D g);
    protected abstract void bufferRepaint();
    @Override
	protected abstract void paintComponent(Graphics g);
    
    public Overlay getOverlay() {
        return overlay;
    }

    public void setOverlay(Overlay o) {
        overlay = o;
        bufferRepaint();
    }

    protected void addAction(String keyStroke, String name, Action a) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(keyStroke), name);
        getActionMap().put(name, a);
    }

    public BufferCanvas(Continuation c) {
        super();
        setOpaque(true);
        setDoubleBuffered(false);
        theWayOut = c;
    }
}
