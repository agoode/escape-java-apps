package org.spacebar.escape.solver;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Bot;
import org.spacebar.escape.common.hash.FNV32;
import org.spacebar.escape.common.hash.FNV64;
import org.spacebar.escape.common.hash.MD5;

public class Level extends org.spacebar.escape.common.Level {

    public Level(Level l) {
        super(l);
    }

    public Level(BitInputStream in) throws IOException {
        super(in);
    }

    @Override
    public int hashCode() {
        // Like Tom,
        /*
         * ignore title, author, w/h, dests, flags, since these don't change.
         * also ignore botd and guyd, which are presentational.
         */

        FNV32 hash = new FNV32();

        // player
        hash.fnv32((byte) player.getX());
        hash.fnv32((byte) player.getY());

        // tiles, oTiles
        // hash.fnv32(width);
        // hash.fnv32(height);
        for (int i = 0; i < tiles.length; i++) {
            hash.fnv32(tiles[i]);
            hash.fnv32(oTiles[i]);
            hash.fnv32(flags[i]);
            // hash.fnv32(dests[i]);
        }

        // bots
        hash.fnv32((byte) bots.length);
        for (int i = 0; i < bots.length; i++) {
            Bot b = bots[i];
            hash.fnv32(b.getBotType());
            hash.fnv32((byte) b.getX());
            hash.fnv32((byte) b.getY());
        }

        return hash.hval;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Level) {
            Level l = (Level) obj;

            // entities
            if (!player.equals(l.player)) {
                return false;
            }

            for (int i = 0; i < bots.length; i++) {
                if (!bots[i].equals(l.bots[i])) {
                    return false;
                }
            }

            /*
             * // metadata if (!author.equals(l.author)) { return false; } if
             * (!title.equals(l.title)) { return false; } if (width != l.width) {
             * return false; } if (height != l.height) { return false; }
             */

            // tiles
            boolean tilesEq = tiles == l.tiles;
            boolean oTilesEq = oTiles == l.oTiles;
            boolean flagsEq = flags == l.flags;
            boolean destsEq = dests == l.dests;

            if (!tilesEq || !oTilesEq || !flagsEq || !destsEq) {
                if (!tilesEq) {
                    for (int i = 0; i < tiles.length; i++) {
                        if (tiles[i] != l.tiles[i]) {
                            return false;
                        }
                    }
                }
                if (!oTilesEq) {
                    for (int i = 0; i < oTiles.length; i++) {
                        if (oTiles[i] != l.oTiles[i]) {
                            return false;
                        }
                    }
                }
                if (!flagsEq) {
                    for (int i = 0; i < flags.length; i++) {
                        if (flags[i] != l.flags[i]) {
                            return false;
                        }
                    }
                }
                if (!destsEq) {
                    for (int i = 0; i < dests.length; i++) {
                        if (dests[i] != l.dests[i]) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "[\"" + title + "\" by " + author + " (" + width + "x" + height
                + ")" + " player: (" + this.player.getX() + ","
                + this.player.getY() + ")]";
    }

    public MD5 MD5() {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            return new MD5(m.digest(importantBits()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] importantBits() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(baos);
        // player
        d.writeInt(player.getX());
        d.writeInt(player.getY());

        // tiles, oTiles
        d.writeInt(width);
        d.writeInt(height);
        for (int i = 0; i < tiles.length; i++) {
            d.writeInt(tiles[i]);
            d.writeInt(oTiles[i]);
            d.writeInt(flags[i]);
            d.writeInt(dests[i]);
        }

        // bots
        d.writeInt(bots.length);
        for (int i = 0; i < bots.length; i++) {
            Bot b = bots[i];
            d.writeInt(b.getBotType());
            d.writeInt(b.getX());
            d.writeInt(b.getY());
        }

        d.close();
        return baos.toByteArray();
    }

    long quickHash() {
        FNV64 hash = new FNV64();

        // player
        hash.fnv64(player.getX());
        hash.fnv64(player.getY());

        // tiles, oTiles
        hash.fnv64(width);
        hash.fnv64(height);
        for (int i = 0; i < tiles.length; i++) {
            hash.fnv64(tiles[i]);
            hash.fnv64(oTiles[i]);
            hash.fnv64(flags[i]);
            hash.fnv64(dests[i]);
        }

        // bots
        hash.fnv64(bots.length);
        for (int i = 0; i < bots.length; i++) {
            Bot b = bots[i];
            hash.fnv64(b.getBotType());
            hash.fnv64(b.getX());
            hash.fnv64(b.getY());
        }

        return hash.hval;
    }

    // @Override
    // protected void finalize() throws Throwable {
    // System.out.println("bye bye " + this);
    // super.finalize();
    // }
}
