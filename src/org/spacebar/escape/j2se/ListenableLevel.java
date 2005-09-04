/*
 * Created on Apr 1, 2005
 */
package org.spacebar.escape.j2se;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Effects;
import org.spacebar.escape.solver.Level;

public class ListenableLevel extends Level {

    public ListenableLevel(BitInputStream in) throws IOException {
        super(in);
    }
    public ListenableLevel(Level l) {
        super(l);
    }

    List<MoveListener> moveListeners = new ArrayList<MoveListener>();
    
    @Override
	public boolean move(byte d, Effects e) {
        boolean r = super.move(d, e);
        afterMove(r);
        return r;
    }

    private void afterMove(boolean success) {
        for (Iterator<MoveListener> i = moveListeners.iterator(); i.hasNext();) {
            MoveListener ml = i.next();
            ml.moveOccurred(success);
        }
    }

    public void addMoveListener(MoveListener m) {
        moveListeners.add(m);
    }
    public void removeMoveListener(MoveListener m) {
        moveListeners.remove(m);
    }
}
