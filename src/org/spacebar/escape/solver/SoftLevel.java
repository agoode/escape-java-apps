package org.spacebar.escape.solver;

import java.io.PrintStream;
import java.lang.ref.SoftReference;

import org.spacebar.escape.common.Entity;
import org.spacebar.escape.common.IntTriple;
import org.spacebar.escape.common.hash.MD5;

public class SoftLevel {
    private final Level baseLevel;

    private final byte dirToHere;
    
    private final SoftLevel parent;
    
    private SoftReference<Level> levelRef;

    private int hashVal;

    private boolean validHashCode;

    public static int regenCount;

    public SoftLevel(Level baseLevel) {
        this.baseLevel = baseLevel;
        parent = null;
        dirToHere = Entity.DIR_NONE;
    }

    private SoftLevel(SoftLevel level, Level l, byte dir) {
        this.baseLevel = null;
        parent = level;
        dirToHere = dir;
        levelRef = new SoftReference<Level>(l);
    }

    private Level getLevel() {
        if (levelRef == null) {
            return baseLevel;
        }
        Level l = levelRef.get();
//        l = null;
        if (l == null) {
            regenCount++;
            // replay
//            System.out.print(".");
//            System.out.flush();
            l = recursiveRebuild();
            levelRef = new SoftReference<Level>(l);
        }
        return l;
    }

    private Level recursiveRebuild() {
        if (baseLevel == null) {
            Level l = parent.recursiveRebuild();
            l.move(dirToHere);
            return l;
        } else {
            return new Level(baseLevel);
        }
    }

    public int destAt(int x, int y) {
        return getLevel().destAt(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SoftLevel) {
            SoftLevel s = (SoftLevel) obj;
//            System.out.println(this + " ?= " + s);
            return getLevel().equals(s.getLevel());
        }
        return false;
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

    public boolean isBotDeleted(int botIndex) {
        return getLevel().isBotDeleted(botIndex);
    }

    public boolean isDead() {
        return getLevel().isDead();
    }

    public boolean isWon() {
        return getLevel().isWon();
    }

    public MD5 MD5() {
        return getLevel().MD5();
    }

    public SoftLevel move(byte d) {
        Level l = new Level(getLevel());
        if (l.move(d)) {
//            System.out.println("move: " + d + "(" + getLevel().getPlayerX() + "," + getLevel().getPlayerY() + ")");
            return new SoftLevel(this, l, d);
        }
//        System.out.println("!move: " + d);
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
}
