package org.spacebar.escape.solver;

import java.io.PrintStream;
import java.lang.ref.SoftReference;

import org.spacebar.escape.common.Entity;
import org.spacebar.escape.common.EquateableLevel;
import org.spacebar.escape.common.IntTriple;

public class SoftLevel {
    final private EquateableLevel baseLevel;

    final SoftLevel parent;

    private SoftReference<EquateableLevel> levelRef;

    private int hashVal;

    final private byte dirToHere;

    private boolean validHashCode;

    static int regenCount;

    public SoftLevel(EquateableLevel baseLevel) {
        this.baseLevel = baseLevel;
        parent = null;
        dirToHere = Entity.DIR_NONE;
        levelRef = new SoftReference<EquateableLevel>(null);
    }

    private SoftLevel(SoftLevel level, EquateableLevel l, byte dir) {
        this.baseLevel = level.baseLevel;
        parent = level;
        dirToHere = dir;
        levelRef = new SoftReference<EquateableLevel>(l);
    }

    private EquateableLevel getLevel() {
        if (dirToHere == Entity.DIR_NONE) {
            return baseLevel;
        }
        // EquateableLevel l = null;
        EquateableLevel l = levelRef.get();

        if (l == null) {
            regenCount++;
            // replay
            // System.out.print(".");
            // System.out.flush();
            l = recursiveRebuild();
            levelRef = new SoftReference<EquateableLevel>(l);
        }
        return l;
    }

    private EquateableLevel recursiveRebuild() {
        if (parent != null) {
            EquateableLevel l = parent.recursiveRebuild();
            l.move(dirToHere);
            return l;
        } else {
            return new EquateableLevel(baseLevel);
        }
    }

    public void flush() {
        levelRef.clear();
    }

    public int destAt(int x, int y) {
        return getLevel().destAt(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SoftLevel)) {
            return false;
        }

        SoftLevel s = (SoftLevel) obj;
        // System.out.println(this + " ?= " + s);
        return getLevel().equals(s.getLevel());
    }

    public byte flagAt(int x, int y) {
        return getLevel().flagAt(x, y);
    }

    public byte flagAt(int i) {
        return getLevel().flagAt(i);
    }

    public String getAuthor() {
        return getLevel().getAuthor();
    }

    public int getBotCount() {
        return getLevel().getBotCount();
    }

    public int getBotDir(int botIndex) {
        return getLevel().getBotDir(botIndex);
    }

    public int getBotType(int index) {
        return getLevel().getBotType(index);
    }

    public int getBotX(int index) {
        return getLevel().getBotX(index);
    }

    public int getBotY(int index) {
        return getLevel().getBotY(index);
    }

    public int getHeight() {
        return getLevel().getHeight();
    }

    public IntTriple getLaser() {
        return getLevel().getLaser();
    }

    public int getPlayerDir() {
        return getLevel().getPlayerDir();
    }

    public int getPlayerX() {
        return getLevel().getPlayerX();
    }

    public int getPlayerY() {
        return getLevel().getPlayerY();
    }

    public String getTitle() {
        return getLevel().getTitle();
    }

    public int getWidth() {
        return getLevel().getWidth();
    }

    @Override
    public int hashCode() {
        if (!validHashCode) {
            hashVal = getLevel().hashCode();
            validHashCode = true;
        }
        return hashVal;
    }

    public boolean isDead() {
        return getLevel().isDead();
    }

    public boolean isWon() {
        return getLevel().isWon();
    }

    public SoftLevel move(byte d) {
        EquateableLevel l = new EquateableLevel(getLevel());
        if (l.move(d)) {
            // System.out.println("move: " + d + "(" + getLevel().getPlayerX() +
            // "," + getLevel().getPlayerY() + ")");
            return new SoftLevel(this, l, d);
        }
        // System.out.println("!move: " + d);
        return null;
    }

    public int oTileAt(int x, int y) {
        return getLevel().oTileAt(x, y);
    }

    public void print(PrintStream p) {
        getLevel().print(p);
    }

    public byte tileAt(int x, int y) {
        return getLevel().tileAt(x, y);
    }

    public int tileAt(int i) {
        return getLevel().tileAt(i);
    }

    @Override
    public String toString() {
        return getLevel().toString();
    }

    public byte getDirToHere() {
        return dirToHere;
    }
}
