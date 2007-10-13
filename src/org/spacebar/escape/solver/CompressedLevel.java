package org.spacebar.escape.solver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;

import org.spacebar.escape.common.EquateableLevel;
import org.spacebar.escape.common.Level;

public class CompressedLevel {
    final private byte data[];
    final private int hash;
    final private byte dirToHere;
    final private CompressedLevel parent;
    final private SoftReference<EquateableLevel> origLevel;
    
    public CompressedLevel(EquateableLevel l, byte dirToHere, CompressedLevel parent) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        Deflater dfl = new Deflater(9);
//        DeflaterOutputStream out = new DeflaterOutputStream(baos, dfl);
        OutputStream out = baos;
        try {
            l.serializeLossily(out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        data = baos.toByteArray();
        hash = l.hashCode();

        this.dirToHere = dirToHere;
        this.parent = parent;
        this.origLevel = new SoftReference<EquateableLevel>(l);
    }
    
    public EquateableLevel getLevel(int width, int height) {
        EquateableLevel l = origLevel.get();
        
        if (l != null) {
            return l;
        }
        
        // else, decompress
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
//        InflaterInputStream in = new InflaterInputStream(bais);
        InputStream in = bais;
        
        try {
            return new EquateableLevel(in, width, height);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public int hashCode() {
        return hash;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        
        if (obj == null) {
            return false;
        }
        
        if (obj instanceof CompressedLevel) {
            CompressedLevel c = (CompressedLevel) obj;

            Arrays.equals(data, c.data);
        }
        return false;
    }

    public CompressedLevel move(byte d, int width, int height) {
        EquateableLevel l = new EquateableLevel(getLevel(width, height));
        l.move(d);
        return new CompressedLevel(l, d, this);
    }

    public boolean isGoal(int width, int height) {
        Level l = getLevel(width, height);
        return l.isWon() && !l.isDead();
    }

    public byte getDirToHere() {
        return dirToHere;
    }

    public CompressedLevel getParent() {
        return parent;
    }
}
