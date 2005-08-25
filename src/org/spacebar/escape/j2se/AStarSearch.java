/*
 * Created on Jan 23, 2005
 */
package org.spacebar.escape.j2se;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.spacebar.escape.common.Entity;

/**
 * @author adam
 */
public class AStarSearch implements Runnable {
    PriorityQueue<AStarNode> open = new PriorityQueue<AStarNode>();
    Set closed = new HashSet();
    List solution;
    
    public AStarSearch(Level l) {
        AStarNode start = new AStarNode(l, 0);
        open.add(start);
    }
    
    class AStarNode implements Comparable {
        Level level;
        int f;
        
        AStarNode(Level l, int cost) {
            this.level = l;
            f = heuristic() + cost;
        }
        
        private int heuristic() {
            return 0;
        }

        public int compareTo(Object o) {
            AStarNode a = (AStarNode) o;
            return f - a.f;
        }
        
        List<AStarNode> getChildren() {
            List<AStarNode> l = new ArrayList<AStarNode>();
            
            for (int i = Entity.FIRST_DIR; i < Entity.LAST_DIR; i++) {
                Level lev = new Level(level);
                if (lev.move(i)) {
                    l.add(new AStarNode(lev, f + 1));
                }
            }
            
            return l;
        }
    }

    public void run() {
        
    }
}
