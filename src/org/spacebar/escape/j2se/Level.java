package org.spacebar.escape.j2se;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Bot;
import org.spacebar.escape.common.hash.FNV32;
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
    	hash.fnv32(player.getX());
    	hash.fnv32(player.getY());
    
    	// tiles, oTiles
        hash.fnv32(width);
        hash.fnv32(height);
    	for (int i = 0; i < tiles.length; i++) {
    		hash.fnv32(tiles[i]);
            hash.fnv32(oTiles[i]);
            hash.fnv32(flags[i]);
            hash.fnv32(dests[i]);
    	}
    
    	// bots
        hash.fnv32(bots.length);
    	for (int i = 0; i < bots.length; i++) {
    		Bot b = bots[i];
    		hash.fnv32(b.getBotType());
    		hash.fnv32(b.getX());
    		hash.fnv32(b.getY());
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
    
            /*
    		// metadata
    		if (!author.equals(l.author)) {
    			return false;
    		}
    		if (!title.equals(l.title)) {
    			return false;
    		}
    		if (width != l.width) {
    			return false;
    		}
    		if (height != l.height) {
    			return false;
    		}
    		*/
            
    		// tiles
    		for (int i = 0; i < tiles.length; i++) {
    			if (tiles[i] != l.tiles[i] || oTiles[i] != l.oTiles[i]
    					|| dests[i] != l.dests[i] || flags[i] != l.flags[i]) {
    				return false;
    			}
    		}
    
    		// entities
    		if (!player.equals(l.player)) {
    			return false;
    		}
    
    		try {
    			for (int i = 0; i < bots.length; i++) {
    				if (!bots[i].equals(l.bots[i])) {
    					return false;
    				}
    			}
    		} catch (ArrayIndexOutOfBoundsException e) {
    			return false;
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
            return new MD5(m.digest(baos.toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
