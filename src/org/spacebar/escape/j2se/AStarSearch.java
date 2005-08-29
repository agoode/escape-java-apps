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

    Set<Long> closed = new HashSet<Long>();
    
    WeakHashMap<Object, Level> levels = new WeakHashMap<Object, Level>();

    // Set<Level> closed = new HashSet<Level>();

    int moveLimit = Integer.MAX_VALUE;

    final private AStarNode start;

    public void setMoveLimit(int moves) {
        moveLimit = moves;
    }

    final int manhattanMap[][];

    final private Level level;

    public AStarSearch(Level l) {
        // construct initial node
        manhattanMap = new int[l.getWidth()][l.getHeight()];
        computeManhattanMap(l);
        level = l;
        start = new AStarNode(null, Entity.DIR_NONE, l, 0);
    }

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

        // initialize
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mmap[x][y] = Integer.MAX_VALUE;
            }
        }

        // find each exit item
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isPossibleExit(l, x, y)) {
                    mmap[x][y] = 0;
                    doBrushFire(mmap, x, y, 1, hugbots + 1);
                }
            }
        }

        // account for transporters
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isPossibleTransport(l, x, y)) {
                    int dest = l.destAt(x, y);
                    int xd = dest % l.getWidth();
                    int yd = dest / l.getWidth();

                    mmap[x][y] = mmap[xd][yd];
                    doBrushFire(mmap, x, y, 1, hugbots + 1);
                }
            }
        }

        // printMmap();
    }

    private boolean isPossibleTransport(Level l, int x, int y) {
        int t = l.tileAt(x, y);
        int o = l.oTileAt(x, y);
        return t == Level.T_TRANSPORT || o == Level.T_TRANSPORT;
    }

    // !
    static private void doBrushFire(int maze[][], int x, int y, int depth,
            int divisor) {
        int val = depth / divisor;
        if (y < maze[0].length - 1 && val < maze[x][y + 1]) {
            maze[x][y + 1] = val;
            doBrushFire(maze, x, y + 1, depth + 1, divisor);
        }
        if (x > 0 && val < maze[x - 1][y]) {
            maze[x - 1][y] = val;
            doBrushFire(maze, x - 1, y, depth + 1, divisor);
        }
        if (y > 0 && val < maze[x][y - 1]) {
            maze[x][y - 1] = val;
            doBrushFire(maze, x, y - 1, depth + 1, divisor);
        }
        if (x < maze.length - 1 && val < maze[x + 1][y]) {
            maze[x + 1][y] = val;
            doBrushFire(maze, x + 1, y, depth + 1, divisor);
        }
    }

    public void printMmap() {
        // print
        for (int x = 0; x < manhattanMap[0].length; x++) {
            for (int y = 0; y < manhattanMap.length; y++) {
                int val = manhattanMap[y][x];
                String s = Integer.toString(val);
                System.out.print(s + " ");
                if (s.length() == 1) {
                    System.out.print(" ");
                }
            }
            System.out.println();
        }
    }

    private void updateOpen(AStarNode a) {
        Level level = getLevel(a);
        assert sanityCheck();
        assert a.g + h(level) <= a.f : a.f + " not <= " + a.g + " + "
                + h(level);
        if (openMap.containsKey(level)) {
            open.removeAll(Collections.singleton(a));
            // open.remove(a);
            assert !open.contains(a);
            openMap.remove(level);
            // System.out.println("old node: " + node);
            // System.out.println("new node: " + a);
        }
        assert sanityCheck();

        open.add(a);
        openMap.put(level, a);
        assert open.size() == openMap.size();
        assert openMap.containsKey(new Level(level));
        assert sanityCheck();
    }

    Level getLevel(AStarNode a) {
        Level l = levels.get(a.key);
        if (l == null) {
            // rebuild
            System.out.print(".");
            System.out.flush();
            
            l = constructLevel(a);
            levels.put(a.key, l);
        }
        
        return l;
    }

    Level constructLevel(AStarNode a) {
        Level l;
        List<Integer> sol = constructSolution(a);
        l = new Level(level);
        for (Integer d : sol) {
            l.move(d);
        }
        return l;
    }

    private AStarNode getFromOpen(AStarNode a) {
        Level level = getLevel(a);
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
        assert open.size() == openMap.size();
        AStarNode a;
        a = open.remove();

        Level aLevel = getLevel(a);
        assert openMap.containsValue(a);
        AStarNode result = openMap.remove(aLevel);
        Level resLevel = getLevel(result);
        assert !openMap.containsValue(a);
        assert aLevel.equals(resLevel);
        assert open.size() == openMap.size();
        assert sanityCheck();
        assert a.g + h(aLevel) <= a.f : a.f + " not <= " + a.g + " + "
                + h(aLevel);
        return a;
    }

    private boolean sanityCheck() {
        List<AStarNode> extraOpenItems = new ArrayList<AStarNode>();
        List<AStarNode> extraOpenMapItems = new ArrayList<AStarNode>();

        boolean bad = false;

        AStarNode head = open.peek();
        int headF = head == null ? 0 : head.f;
        // System.out.println("head node f: " + headF);

        for (AStarNode node : open) {
            if (!openMap.containsValue(node)) {
                extraOpenItems.add(node);
            }
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
        return m;
    }

    private int manhattan(Level l) {
        return manhattanMap[l.getPlayerX()][l.getPlayerY()];
    }

    private boolean isPossibleExit(Level l, int x, int y) {
        int t = l.tileAt(x, y);
        int o = l.oTileAt(x, y);
        return t == Level.T_EXIT || t == Level.T_SLEEPINGDOOR
                || o == Level.T_EXIT || o == Level.T_SLEEPINGDOOR;
    }

    List<AStarNode> generateChildren(AStarNode node) {
        Level level = getLevel(node);
        // default children generation -- override
        List<AStarNode> l = new ArrayList<AStarNode>();

        if (node.g == 0 || (!level.isDead() && !level.isWon())
                && node.g < moveLimit) {
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
            if (!closed.contains(lev.quickHash())) {
                // if (!closed.contains(lev)) {
                l.add(new AStarNode(node, i, lev, 1)); // cost
                // of
                // move is 1
                // lev.print(System.out);
            }
        }
    }

    class AStarNode {
        final AStarNode parent;

        final int dirToGetHere;

        final int f;

        final int g;

        final int hash;
        
        final Object key = new Object();
        
        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AStarNode) {
                Level level = getLevel(this);
                AStarNode item = (AStarNode) obj;
                Level itemLevel = getLevel(item);
                return level.equals(itemLevel);
            }
            return false;
        }

        @Override
        public String toString() {
            Level level = getLevel(this);
            return "(f: " + f + ", g: " + g + ", h: " + h(level) + ", "
                    + level.toString() + ")";
        }

        AStarNode(AStarNode parent, int dir, Level l, int cost) {
            this.parent = parent;
            dirToGetHere = dir;
            hash = l.hashCode();
            
            levels.put(key, l);
            
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
        }

        boolean isGoal() {
            Level level = getLevel(this);
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

    private List<Integer> solution;

    public void run() {
        long time = 0;
        int prevOpen = 0;
        int prevClosed = 0;
        while (!open.isEmpty()) {
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
            // a.level.print(System.out);
            // System.out.println();
            if (a.isGoal()) {
                // GOAL
                solution = constructSolution(a);
                return;
            } else {
                // System.out.println("adding to closed list");
                Level level = getLevel(a);
                closed.add(level.quickHash());
                // closed.add(a.level);
                List<AStarNode> children = generateChildren(a);
                
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
        Level level = getLevel(start);
        assert start.g + h(level) == start.f : start.f + " != " + start.g
                + " + " + h(level);
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
        return count;
    }

    public static void main(String[] args) {
        try {
            Level l = new Level(
                    new BitInputStream(new FileInputStream(args[0])));

            int startingMoves = 2;
            if (args.length > 1) {
                startingMoves = Integer.parseInt(args[1]);
            }

            AStarSearch search2 = new AStarSearch(l) {
                @Override
                public int h(Level l) {
                    return countSpheres(l);
                }

                @Override
                public List<AStarNode> generateChildren(AStarNode node) {
                    List<AStarNode> l = new ArrayList<AStarNode>();
                    Level level = getLevel(node);

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
            search.printMmap();
            for (int i = startingMoves; i <= 65536; i *= 2) {
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

    private static void robot(List<Integer> s) {
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
