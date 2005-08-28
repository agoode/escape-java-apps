/*
 * Created on Jan 23, 2005
 */
package org.spacebar.escape.j2se;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import javax.swing.JOptionPane;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Entity;

/**
 * @author adam
 */
public class AStarSearch implements Runnable {
    PriorityQueue<AStarNode> open = new PriorityQueue<AStarNode>(11,
            new AStarPQComparator());

    Map<Level, AStarNode> openMap = new HashMap<Level, AStarNode>();

    Set<Level> closed = new HashSet<Level>();

    int moveLimit = Integer.MAX_VALUE;

    private AStarNode start;

    public void setMoveLimit(int moves) {
        moveLimit = moves;
    }

    public AStarSearch(Level l) {
        // construct initial node
        start = new AStarNode(null, Entity.DIR_NONE, l, 0);
    }

    private void updateOpen(AStarNode a) {
        assert sanityCheck();
        if (openMap.containsKey(a.level)) {
            open.removeAll(Collections.singleton(a));
            assert !open.contains(a);
            openMap.remove(a.level);
        }
        assert sanityCheck();

        open.add(a);
        openMap.put(a.level, a);
        assert open.size() == openMap.size();
        assert openMap.containsKey(new Level(a.level));
        assert sanityCheck();
    }

    private AStarNode getFromOpen(AStarNode a) {
        assert sanityCheck();
        AStarNode node = openMap.get(a.level);
        assert sanityCheck();
        return node;
    }

    private AStarNode removeFromOpen() {
        assert sanityCheck();
        // System.out.println("AStarSearch.removeFromOpen()");
        assert open.size() == openMap.size();
        AStarNode a;
        a = open.remove();

        assert openMap.containsValue(a);
        AStarNode result = openMap.remove(a.level);
        assert !openMap.containsValue(a);
        assert a.level.equals(result.level);
        assert open.size() == openMap.size();
        assert sanityCheck();
        return a;
    }

    private boolean sanityCheck() {
        List<AStarNode> extraOpenItems = new ArrayList<AStarNode>();
        List<AStarNode> extraOpenMapItems = new ArrayList<AStarNode>();

        for (AStarNode node : open) {
            if (!openMap.containsValue(node)) {
                extraOpenItems.add(node);
            }
        }

        for (AStarNode node : openMap.values()) {
            if (!open.contains(node)) {
                extraOpenMapItems.add(node);
            }
        }

        boolean bad = false;
        if (!extraOpenItems.isEmpty()) {
            System.out.println("extra items in open: " + extraOpenItems);
            bad = true;
        }
        if (!extraOpenMapItems.isEmpty()) {
            System.out.println("extra items in openMap: " + extraOpenMapItems);
            bad = true;
        }

        return !bad;
    }

    int h(Level l) {
        // default heuristic -- override
        int m = manhattan(l);
        bestH = Math.min(bestH, m);
        return m;
    }

    private int manhattan(Level l) {
        int dist = Integer.MAX_VALUE;
        boolean found = false;

        final int px = l.getPlayerX();
        final int py = l.getPlayerY();
        for (int y = 0; y < l.getHeight(); y++) {
            for (int x = 0; x < l.getWidth(); x++) {
                int t = l.tileAt(x, y);
                // if (t == Level.T_EXIT || t == Level.T_0 || t == Level.T_1
                // || t == Level.T_BROKEN || t == Level.T_BSPHERE
                // || t == Level.T_BSTEEL || t == Level.T_BUTTON
                // || t == Level.T_GOLD || t == Level.T_GREEN
                // || t == Level.T_GREY || t == Level.T_GSPHERE
                // || t == Level.T_GSTEEL
                // || t == Level.T_ON || t == Level.T_PANEL
                // || t == Level.T_RED || t == Level.T_RSPHERE
                // || t == Level.T_RSTEEL || t == Level.T_SPHERE
                // || t == Level.T_TRANSPONDER || t == Level.T_TRANSPORT) {
                if (t == Level.T_EXIT || t == Level.T_HEARTFRAMER) {
                    int d = Math.abs(x - px) + Math.abs(y - py);
                    dist = Math.min(dist, d);
                    found = true;
                }
            }
        }
        return found ? dist : 0;
    }

    List<AStarNode> generateChildren(AStarNode node) {
        // default children generation -- override
        List<AStarNode> l = new ArrayList<AStarNode>();
        Level level = node.level;

        if (!level.isDead() && !level.isWon() && node.g < moveLimit) {
            for (int i = Entity.FIRST_DIR; i <= Entity.LAST_DIR; i++) {
                Level lev = new Level(level);
                testChild(node, l, i, lev);
            }
            // System.out.println("----------");
        }

        return l;

    }

    protected void testChild(AStarNode node, List<AStarNode> l, int i, Level lev) {
        if (lev.move(i)) {
            // System.out.println(" child");
            if (!closed.contains(lev)) {
                l.add(new AStarNode(node, i, lev, node.g + 1)); // cost
                // of
                // move is 1
                // lev.print(System.out);
            }
        }
    }

    class AStarNode {
        final AStarNode parent;

        final int dirToGetHere;

        final Level level;

        final int f;

        final int g;

        @Override
        public int hashCode() {
            return level.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AStarNode) {
                AStarNode item = (AStarNode) obj;
                return level.equals(item.level);
            }
            return false;
        }

        @Override
        public String toString() {
            return "(f: " + f + "," + level.toString() + ")";
        }

        AStarNode(AStarNode parent, int dir, Level l, int g) {
            this.parent = parent;
            dirToGetHere = dir;
            this.level = l;
            this.g = g;
            int parentF = parent == null ? 0 : parent.f;
            f = Math.max(parentF, g + h(l)); // pathmax!
        }

        boolean isGoal() {
            boolean result = !level.isDead() && level.isWon();
            if (result) {
                System.out.println("GOAL");
            }
            return result;
        }

    }

    private class AStarPQComparator implements Comparator<AStarNode> {
        public int compare(AStarNode o1, AStarNode o2) {
            if (o1.f < o2.f) {
                return -1;
            } else if (o1.f > o2.f) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    int bestH;

    private List<Integer> solution;

    public void run() {
        long time = 0;
        while (!open.isEmpty()) {
            if (System.currentTimeMillis() - time > 1000) {
                System.out.println("Open nodes: " + open.size()
                        + ", closed nodes: " + closed.size());
                // System.out.println("best h: " + bestH);
                time = System.currentTimeMillis();
            }
            AStarNode a = removeFromOpen();
            // System.out.println("getting node from open list, f: " + a.f);
            // a.level.print(System.out);
            // System.out.println();
            if (a.isGoal()) {
                // GOAL
                solution = constructSolution(a);
                return;
            } else {
                // System.out.println("adding to closed list");
                closed.add(a.level);
                List<AStarNode> children = generateChildren(a);
                for (AStarNode node : children) {
                    AStarNode oldNode = getFromOpen(node);
                    if (oldNode == null) {
                        updateOpen(node);
                    } else if (node.g < oldNode.g) {
                        assert node.equals(oldNode);
                        assert node.f < oldNode.f;
                        updateOpen(node);
                    }
                }
            }
        }
        System.out.println("closed nodes: " + closed.size());
        solution = null;
    }

    public void initialize() {
        assert sanityCheck();
        // init lists
        // open.clear();
        // openMap.clear();
        closed.clear();
        assert sanityCheck();

        // add to open list
        updateOpen(start);

        // init
        bestH = Integer.MAX_VALUE;
    }

    private List<Integer> constructSolution(AStarNode a) {
        List<Integer> moves = new ArrayList<Integer>();
        while (a != null) {
            moves.add(a.dirToGetHere);
            a = a.parent;
        }
        Collections.reverse(moves);
        moves.remove(0);

        return moves;
    }

    public boolean solutionFound() {
        return solution != null;
    }

    public void printSolution() {
        if (solution == null) {
            System.out.println("No solution found");
            return;
        }

        System.out.println("moves: " + solution.size());
        int lastMove = Entity.DIR_NONE;
        int moveCount = 0;
        for (Integer move : solution) {
            if (move != lastMove) {
                if (lastMove != Entity.DIR_NONE) {
                    System.out.print(moveCount
                            + Entity.directionToString(lastMove) + " ");
                }
                lastMove = move;
                moveCount = 1;
            } else {
                moveCount++;
            }
        }
        System.out.println(moveCount + Entity.directionToString(lastMove));
    }

    int countSpheres(Level l) {
        // number of spheres
        int count = 0;
        for (int i = 0; i < l.getWidth() * l.getHeight(); i++) {
            if (l.tileAt(i) == Level.T_BSPHERE) {
                count++;
            }
        }
        if (count < bestH) {
            bestH = count;
        }
        return count;
    }

    public static void main(String[] args) {
        try {
            Level l = new Level(
                    new BitInputStream(new FileInputStream(args[0])));

            AStarSearch search2 = new AStarSearch(l) {
                @Override
                public int h(Level l) {
                    return countSpheres(l);
                }

                @Override
                public List<AStarNode> generateChildren(AStarNode node) {
                    List<AStarNode> l = new ArrayList<AStarNode>();
                    Level level = node.level;

                    if (!level.isDead() && !level.isWon() && node.g < moveLimit) {
                        if (countSpheres(level) == 0) {
                            for (int i = Entity.FIRST_DIR; i <= Entity.LAST_DIR; i++) {
                                Level lev = new Level(level);
                                testChild(node, l, i, lev);
                            }
                        } else {
                            if (level.getPlayerY() == 3) { // sphererow
                                Level lev = new Level(level);
                                testChild(node, l, Entity.DIR_UP, lev);
                                lev = new Level(level);
                                testChild(node, l, Entity.DIR_RIGHT, lev);
                            } else {
                                Level lev = new Level(level);
                                testChild(node, l, Entity.DIR_DOWN, lev);
                                lev = new Level(level);
                                testChild(node, l, Entity.DIR_LEFT, lev);
                                lev = new Level(level);
                                testChild(node, l, Entity.DIR_RIGHT, lev);
                            }
                        }
                    }

                    return l;
                }
            };

            l.print(System.out);

            AStarSearch search = new AStarSearch(l);
            for (int i = 10; i <= 10000; i += 5) {
                System.out.print("trying in " + i + " moves, ");
                search.initialize();
                search.setMoveLimit(i);

                search.run();

                if (search.solutionFound()) {
                    search.printSolution();
                    robot(search.solution);
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void robot(List<Integer> s) {
        String options[] = { "Type The Solution", "Exit" };
        int result = JOptionPane.showOptionDialog(null,
                "Do you want to run the solution in 5 seconds?",
                "Solution Found", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        if (result == JOptionPane.YES_OPTION) {
            Robot r = null;
            try {
                r = new Robot();
            } catch (AWTException e) {
                e.printStackTrace();
                return;
            }

            r.setAutoDelay(40);

            System.out.print("Running in 5 seconds...");
            System.out.flush();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println();
            
            for (int dir : s) {
                System.out.print(Entity.directionToString(dir) + " ");
                System.out.flush();

                int k = directionToKey(dir);
                r.keyPress(k);
                r.keyRelease(k);
            }
            System.out.println();
        }
    }

    private static int directionToKey(int dir) {
        switch (dir) {
        case Entity.DIR_UP:
            return KeyEvent.VK_UP;
        case Entity.DIR_DOWN:
            return KeyEvent.VK_DOWN;
        case Entity.DIR_LEFT:
            return KeyEvent.VK_LEFT;
        case Entity.DIR_RIGHT:
            return KeyEvent.VK_RIGHT;

        default:
            assert false;
            return 0;
        }
    }
}
