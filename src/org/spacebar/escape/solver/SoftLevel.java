package org.spacebar.escape.solver;

import java.io.PrintStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.spacebar.escape.common.Effects;
import org.spacebar.escape.common.IntTriple;
import org.spacebar.escape.common.Level.DirtyList;
import org.spacebar.escape.common.hash.MD5;

public class SoftLevel {
    private final Level baseLevel;

    private final List path;

    private SoftReference levelRef;

    private int hashVal;

    private boolean validHashCode;

    public SoftLevel(Level baseLevel) {
        this.baseLevel = baseLevel;
        path = new ArrayList();
        levelRef = new SoftReference(new Level(baseLevel));
    }

    public SoftLevel(SoftLevel level) {
        this.baseLevel = level.baseLevel;
        this.path = new ArrayList(level.path);
        levelRef = new SoftReference(new Level(level.getLevel()));
    }

    private Level getLevel() {
        Level l = (Level) levelRef.get();
        if (l == null) {
            // replay
//            System.out.print(".");
//            System.out.flush();
            l = new Level(baseLevel);
            Iterator it = path.iterator();
            while(it.hasNext()) {
                byte d = ((Byte)it.next()).byteValue();
                l.move(d);
            }
            levelRef = new SoftReference(l);
        }
        return l;
    }

    public int destAt(int x, int y) {
        return getLevel().destAt(x, y);
    }

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

    public DirtyList getDirty() {
        return getLevel().getDirty();
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

    public boolean move(byte d, Effects e) {
        if (getLevel().move(d, e)) {
            path.add(new Byte(d));
            validHashCode = false;
            return true;
        }
        return false;
    }

    public boolean move(byte d) {
        if (getLevel().move(d)) {
//            System.out.println("move: " + d + "(" + getLevel().getPlayerX() + "," + getLevel().getPlayerY() + ")");
            path.add(new Byte(d));
            validHashCode = false;
            return true;
        }
//        System.out.println("!move: " + d);
        return false;
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

    public String toString() {
        return getLevel().toString();
    }
}
