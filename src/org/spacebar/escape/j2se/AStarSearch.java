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

    Map<SoftLevel, AStarNode> openMap = new HashMap<SoftLevel, AStarNode>();

    // Set<Long> closed = new HashSet<Long>();
    Set<SoftLevel> closed = new HashSet<SoftLevel>();

    // Set<Level> closed = new HashSet<Level>();

    int moveLimit = Integer.MAX_VALUE;

    final private AStarNode start;

    public void setMoveLimit(int moves) {
        moveLimit = moves;
    }

    final int manhattanMap[][];

    int greatestG;

    public AStarSearch(Level l) {
        // construct initial node
        manhattanMap = new int[l.getWidth()][l.getHeight()];
        computeManhattanMap(l);
        start = new AStarNode(null, Entity.DIR_NONE, new SoftLevel(l), 0);
    }

    // XXX somehow broken, see lev560
    private void computeManhattanMap(Level l) {
        // get number of hugbots, they can push us closer
        int hugbots = 0;
        for (int i = 0; i < l.getBotCount(); i++) {
            int bot = l.getBotType(i);
            if (bot == Entity.B_HUGBOT || bot == Entity.B_HUGBOT_ASLEEP) {
                hugbots++;
            }
        }

        int mmap[][] = manhattanMap;
        int w = l.getWidth();
        int h = l.getHeight();
        boolean panelDests[][] = new boolean[w][h];
        boolean transportDests[][] = new boolean[w][h];

        // initialize
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mmap[x][y] = Integer.MAX_VALUE / 2; // avoid overflow!!
                int dest = l.destAt(x, y);
                int tx = dest % w;
                int ty = dest / w;
                int t = l.tileAt(x, y);
                int o = l.oTileAt(x, y);
                if (t == Level.T_TRANSPORT || o == Level.T_TRANSPORT) {
                    // XXX see if transport is in bizarro world
                    // but is never a dest itself
                    transportDests[tx][ty] = true;
                }
                if (Level.isPanel(t) || Level.isPanel(o)) {
                    // XXX see if panel is in bizarro world
                    // but is never a dest itself
                    panelDests[tx][ty] = true;
                }
            }
        }

        // find each exit item
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isPossibleExit(l, x, y, panelDests[x][y])) {
                    mmap[x][y] = 0;
                    doBrushFire(mmap, l, x, y, 1, hugbots + 1, panelDests);
                }
            }
        }

        // account for transporters
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isPossibleTransport(l, x, y, panelDests[x][y])) {
                    int dest = l.destAt(x, y);
                    int xd = dest % l.getWidth();
                    int yd = dest / l.getWidth();

                    mmap[x][y] = mmap[xd][yd];
                    doBrushFire(mmap, l, x, y, 1, hugbots + 1, panelDests);
                }
            }
        }

        // printMmap();
    }

    static private boolean isPossibleTransport(Level l, int x, int y,
            boolean isPanelTarget) {
        int t = l.tileAt(x, y);
        int o = l.oTileAt(x, y);
        return t == Level.T_TRANSPORT
                || (o == Level.T_TRANSPORT && isPanelTarget);
    }

    // !
    static private void doBrushFire(int maze[][], Level l, int x, int y,
            int depth, int divisor, boolean panelDests[][]) {
        int val = depth / divisor;
        doBrushFire2(maze, l, x, y + 1, depth, divisor, panelDests, val);
        doBrushFire2(maze, l, x, y - 1, depth, divisor, panelDests, val);
        doBrushFire2(maze, l, x + 1, y, depth, divisor, panelDests, val);
        doBrushFire2(maze, l, x - 1, y, depth, divisor, panelDests, val);
    }

    /**
     * @param maze
     * @param l
     * @param x
     * @param y
     * @param depth
     * @param divisor
     * @param panelDests
     * @param val
     */
    private static void doBrushFire2(int[][] maze, Level l, int x, int y,
            int depth, int divisor, boolean[][] panelDests, int val) {
        if (!isBoundary(l, x, y, panelDests) && val < maze[x][y]) {
            maze[x][y] = val;
            doBrushFire(maze, l, x, y, depth + 1, divisor, panelDests);
        }
    }

    static private boolean isBoundary(Level l, int x, int y,
            boolean panelDests[][]) {
        int w = l.getWidth();
        int h = l.getHeight();

        // bounds
        if (x < 0 || x >= w || y < 0 || y >= h) {
            return true;
        }

        int t = l.tileAt(x, y);
        int o = l.oTileAt(x, y);
        return isImmovableTile(t) || (isImmovableTile(o) && panelDests[x][y]);
    }

    /**
     * @param t
     * @return
     */
    private static boolean isImmovableTile(int t) {
        return t == Level.T_BLUE || t == Level.T_LASER || t == Level.T_STOP
                || t == Level.T_RIGHT || t == Level.T_LEFT || t == Level.T_UP
                || t == Level.T_DOWN || t == Level.T_ON || t == Level.T_OFF
                || t == Level.T_0 || t == Level.T_1 || t == Level.T_BUTTON
                || t == Level.T_BLIGHT || t == Level.T_RLIGHT
                || t == Level.T_GLIGHT || t == Level.T_BLACK;
    }

    public void printMmap() {
        // print
        for (int x = 0; x < manhattanMap[0].length; x++) {
            for (int y = 0; y < manhattanMap.length; y++) {
                int val = manhattanMap[y][x];
                String s;
                if (val < Integer.MAX_VALUE / 2) {
                    s = Integer.toString(val);
                } else {
                    s = "*";
                }
                System.out.print(s + " ");
                if (s.length() == 1) {
                    System.out.print(" ");
                }
            }
            System.out.println();
        }
    }

    private void updateOpen(AStarNode a) {
        SoftLevel level = a.level;
        assert sanityCheck();
        assert a.g + h(level) <= a.f : a.f + " not <= " + a.g + " + "
                + h(level);
        // if (openMap.containsKey(level)) {
        // open.removeAll(Collections.singleton(a));
        // // open.remove(a);
        // assert !open.contains(a);
        // openMap.remove(level);
        // // System.out.println("old node: " + node);
        // // System.out.println("new node: " + a);
        // }
        // assert sanityCheck();

        open.add(a);
        openMap.put(level, a);
        assert open.size() >= openMap.size();
        // assert openMap.containsKey(new Level(level));
        assert sanityCheck();
    }

    private AStarNode getFromOpen(AStarNode a) {
        SoftLevel level = a.level;
        assert a.g + h(level) <= a.f : a.f + " not <= " + a.g + " + "
                + h(level);
        assert sanityCheck();
        AStarNode node = openMap.get(level);
        assert sanityCheck();
        return node;
    }

    private AStarNode removeFromOpen() {
        assert sanityCheck();
        // System.out.println("AStarSearch.removeFromOpen()");
        assert open.size() >= openMap.size();
        AStarNode a;
        AStarNode tmp;
        do {
            a = open.remove();
            SoftLevel l = a.level;
            if (openMap.containsKey(l)) {
                tmp = openMap.get(l);
            } else {
                tmp = null;
            }
        } while (tmp == null || tmp.f > a.f);

        SoftLevel aLevel = a.level;
        assert openMap.containsValue(a);
        AStarNode result = openMap.remove(aLevel);
        SoftLevel resLevel = result.level;
        assert !openMap.containsValue(a);
        assert aLevel.equals(resLevel);
        assert open.size() >= openMap.size();
        assert sanityCheck();
        assert a.g + h(aLevel) <= a.f : a.f + " not <= " + a.g + " + "
                + h(aLevel);
        return a;
    }

    private boolean sanityCheck() {
        // List<AStarNode> extraOpenItems = new ArrayList<AStarNode>();
        List<AStarNode> extraOpenMapItems = new ArrayList<AStarNode>();

        boolean bad = false;

        AStarNode head = open.peek();
        int headF = head == null ? 0 : head.f;
        // System.out.println("head node f: " + headF);

        for (AStarNode node : open) {
            // if (!openMap.containsValue(node)) {
            // extraOpenItems.add(node);
            // }
            if (headF > node.f) {
                System.out.println("head node has non-best f: " + headF
                        + " (better f: " + node.f + ")");
                bad = true;
            }
        }

        for (AStarNode node : openMap.values()) {
            if (!open.contains(node)) {
                extraOpenMapItems.add(node);
            }
        }

        // if (!extraOpenItems.isEmpty()) {
        // System.out.println("extra items in open: " + extraOpenItems);
        // bad = true;
        // }
        if (!extraOpenMapItems.isEmpty()) {
            System.out.println("extra items in openMap: " + extraOpenMapItems);
            bad = true;
        }

        return !bad;
    }

    int h(SoftLevel l) {
        // default heuristic -- override
        int m = manhattan(l);

        // covered colors
        // int coveredColors = computeCoveredColors(l);

        return m;
    }

    static private int computeCoveredColors(SoftLevel l) {
        int coveredColors = 0;
        for (int i = 0; i < l.getWidth() * l.getHeight(); i++) {
            int t = l.tileAt(i);
            int f = l.flagAt(i);
            if ((f & Level.TF_HASPANEL) == 0) {
                // no panel
                continue;
            }

            switch (t) {
            case Level.T_BSPHERE:
            case Level.T_BSTEEL:
                if (Level.realPanel(f) == Level.T_BPANEL) {
                    coveredColors++;
                }
                break;

            case Level.T_RSPHERE:
            case Level.T_RSTEEL:
                if (Level.realPanel(f) == Level.T_RPANEL) {
                    coveredColors++;
                }
                break;

            case Level.T_GSPHERE:
            case Level.T_GSTEEL:
                if (Level.realPanel(f) == Level.T_GPANEL) {
                    coveredColors++;
                }
                break;
            }
        }
        return coveredColors;
    }

    private int manhattan(SoftLevel l) {
        return manhattanMap[l.getPlayerX()][l.getPlayerY()];
    }

    static private boolean isPossibleExit(Level l, int x, int y,
            boolean isPanelTarget) {
        int t = l.tileAt(x, y);
        int o = l.oTileAt(x, y);
        // XXX check to see if heartframers are accessible ?
        return t == Level.T_EXIT
                || t == Level.T_SLEEPINGDOOR
                || (isPanelTarget && (o == Level.T_EXIT || o == Level.T_SLEEPINGDOOR));
    }

    List<AStarNode> generateChildren(AStarNode node) {
        SoftLevel level = node.level;
        // default children generation -- override
        List<AStarNode> l = new ArrayList<AStarNode>();

        if (node.g == 0 || (!level.isDead() && !level.isWon())
                && node.g < moveLimit) {
            for (byte i = Entity.FIRST_DIR; i <= Entity.LAST_DIR; i++) {
                SoftLevel lev = new SoftLevel(node.level);
                testChild(node, l, i, lev);
            }
            // System.out.println("----------");
        }
        // System.out.println("number of children: " + l.size());
        return l;

    }

    protected void testChild(AStarNode node, List<AStarNode> l, byte i,
            SoftLevel lev) {
        if (lev.move(i)) {
            // System.out.println(" child");
            // if (!closed.contains(lev.quickHash())) {
            if (!closed.contains(lev)) {
                // System.out.println("***adding " + lev);
                l.add(new AStarNode(node, i, lev, 1)); // cost
                // of
                // move is 1
                // lev.print(System.out);
            }
        }
    }

    class AStarNode {
        public final SoftLevel level;

        final AStarNode parent;

        final byte dirToGetHere;

        final int f;

        final int g;

        final int hash;

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AStarNode) {
                AStarNode item = (AStarNode) obj;
                if (hash == item.hash) {
                    SoftLevel itemLevel = item.level;
                    return level.equals(itemLevel);
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "(f: " + f + ", g: " + g + ", h: " + h(level) + ", "
                    + level.toString() + ")";
        }

        AStarNode(AStarNode parent, byte dir, SoftLevel l, int cost) {
            this.parent = parent;
            dirToGetHere = dir;
            hash = l.hashCode();

            level = l;

            final int parentF;
            if (parent == null) {
                this.g = cost;
                parentF = 0;
            } else {
                this.g = parent.g + cost;
                parentF = parent.f;
            }

            f = g + h(l);
            assert f >= parentF : "pathmax active! " + parent + " " + this;
            if (g > greatestG) {
                System.out.println("greatest g: " + greatestG);
                greatestG = g;
            }
        }

        boolean isGoal() {
            boolean result = !level.isDead() && level.isWon() && g > 0;
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

    private List<Byte> solution;

    public void run() {
        long time = 0;
        int prevOpen = 0;
        int prevClosed = 0;
        while (!open.isEmpty()) {
            // System.out.println(" ***** closed: " + closed);
            if (System.currentTimeMillis() - time > 1000) {
                int os = openMap.size();
                int cs = closed.size();
                System.out.println("Open nodes: " + os + ", closed nodes: "
                        + cs + "   (deltas: " + (os - prevOpen) + ", "
                        + (cs - prevClosed) + ")");
                // System.out.println("best h: " + bestH);
                prevOpen = os;
                prevClosed = cs;
                time = System.currentTimeMillis();
            }
            AStarNode a = removeFromOpen();
            // System.out.println("getting node from open list, f: " + a.f);
            // System.out.println(a);
            // a.level.print(System.out);
            // System.out.println();
            if (a.isGoal()) {
                // GOAL
                solution = constructSolution(a);
                return;
            } else {
                // System.out.println("adding to closed list");
                SoftLevel level = a.level;
                // closed.add(level.quickHash());
                // System.out.println(" ***** closed: " + closed);
                closed.add(level);
                // System.out.println(" ***** closed: " + closed);
                List<AStarNode> children = generateChildren(a);
                // System.out.println(" ***** closed: " + closed);

                Collections.shuffle(children);
                for (AStarNode node : children) {
                    AStarNode oldNode = getFromOpen(node);
                    if (oldNode == null) {
                        updateOpen(node);
                    } else if (node.g < oldNode.g) {
                        assert node.equals(oldNode);
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
        open.clear();
        openMap.clear();
        closed.clear();
        assert sanityCheck();

        // add to open list
        updateOpen(start);
        SoftLevel level = start.level;
        assert start.g + h(level) == start.f : start.f + " != " + start.g
                + " + " + h(level);

        greatestG = 0;
    }

    List<Byte> constructSolution(AStarNode a) {
        List<Byte> moves = new ArrayList<Byte>();
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

        printSolution(solution);
    }

    static void printSolution(List<Byte> solution) {
        System.out.println("moves: " + solution.size());
        byte lastMove = Entity.DIR_NONE;
        int moveCount = 0;
        for (Byte move : solution) {
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

    int countSpheres(SoftLevel l) {
        // number of spheres
        int count = 0;
        for (int i = 0; i < l.getWidth() * l.getHeight(); i++) {
            if (l.tileAt(i) == Level.T_BSPHERE) {
                count++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        try {
            Level l = new Level(
                    new BitInputStream(new FileInputStream(args[0])));

            int startingMoves = 60;
            if (args.length > 1) {
                startingMoves = Integer.parseInt(args[1]);
            }

            // AStarSearch search2 = new AStarSearch(l) {
            // @Override
            // public int h(SoftLevel l) {
            // return countSpheres(l);
            // }
            //
            // @Override
            // public List<AStarNode> generateChildren(AStarNode node) {
            // List<AStarNode> l = new ArrayList<AStarNode>();
            // SoftLevel level = node.level;
            //
            // if (!level.isDead() && !level.isWon() && node.g < moveLimit) {
            // if (countSpheres(level) == 0) {
            // for (byte i = Entity.FIRST_DIR; i <= Entity.LAST_DIR; i++) {
            // SoftLevel lev = new SoftLevel(level);
            // testChild(node, l, i, lev);
            // }
            // } else {
            // if (level.getPlayerY() == 3) { // sphererow
            // SoftLevel lev = new SoftLevel(level);
            // testChild(node, l, Entity.DIR_UP, lev);
            // lev = new SoftLevel(level);
            // testChild(node, l, Entity.DIR_RIGHT, lev);
            // } else {
            // SoftLevel lev = new SoftLevel(level);
            // testChild(node, l, Entity.DIR_DOWN, lev);
            // lev = new SoftLevel(level);
            // testChild(node, l, Entity.DIR_LEFT, lev);
            // lev = new SoftLevel(level);
            // testChild(node, l, Entity.DIR_RIGHT, lev);
            // }
            // }
            // }
            //
            // return l;
            // }
            // };

            l.print(System.out);

            AStarSearch search = new AStarSearch(l);
            search.printMmap();
            for (int i = startingMoves; i <= startingMoves; i *= 2) {
                System.out.print("trying in " + i + " moves, ");
                search.initialize();
                search.setMoveLimit(i);

                search.run();

                if (search.solutionFound()) {
                    System.out.println(l);
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

    private static void robot(List<Byte> s) {
        int time = 2;
        String options[] = { "Type The Solution", "Exit" };
        int result = JOptionPane.showOptionDialog(null,
                "Do you want to run the solution?", "Solution Found",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                options, options[0]);

        if (result == JOptionPane.YES_OPTION) {
            Robot r = null;
            try {
                r = new Robot();
            } catch (AWTException e) {
                e.printStackTrace();
                return;
            }

            r.setAutoDelay(40);

            System.out.print("Running in " + time + " seconds...");
            System.out.flush();
            try {
                Thread.sleep(time * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println();

            for (byte dir : s) {
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
