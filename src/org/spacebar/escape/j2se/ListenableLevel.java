/*
 * Created on Apr 1, 2005
 * 
 * TODO To change the template for this generated file go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
package org.spacebar.escape.j2se;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Effects;
import org.spacebar.escape.common.Level;

public class ListenableLevel extends Level {

    public ListenableLevel(BitInputStream in) throws IOException {
        super(in);
    }
    public ListenableLevel(Level l) {
        super(l);
    }

    List moveListeners = new ArrayList();
    
    public boolean move(int d, Effects e) {
        boolean r = super.move(d, e);
        afterMove(r);
        return r;
    }

    private void afterMove(boolean success) {
        for (Iterator i = moveListeners.iterator(); i.hasNext();) {
            MoveListener ml = (MoveListener) i.next();
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
