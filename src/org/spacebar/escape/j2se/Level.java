package org.spacebar.escape.j2se;

import java.io.IOException;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Bot;
import org.spacebar.escape.common.hash.FNV32;

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
    	for (int i = 0; i < tiles.length; i++) {
    		hash.fnv32(tiles[i]);
            hash.fnv32(oTiles[i]);
            hash.fnv32(flags[i]);
            hash.fnv32(dests[i]);
    	}
    
    	// bots
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
}
