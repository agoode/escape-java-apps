/*
 * Created on Jan 23, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.spacebar.escape.j2se;

import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.spacebar.escape.common.Level;

/**
 * @author adam
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class AStarSearch implements Runnable {
    PriorityQueue open = new PriorityQueue();
    Set closed = new HashSet();
    List solution;
    
    public AStarSearch(Level l, int px, int py) {
        AStarNode start = new AStarNode(l, px, py, 0);
        open.add(start);
    }
    
    class AStarNode implements Comparable {
        Level l;
        int px, py;
        int f;
        
        AStarNode(Level l, int px, int py, int cost) {
            this.l = l;
            this.px = px;
            this.py = py;
            f = heuristic() + cost;
        }
        
        private int heuristic() {
            return 0;
        }

        public int compareTo(Object o) {
            AStarNode a = (AStarNode) o;
            return f - a.f;
        }
    }

    public void run() {
        
    }
}
