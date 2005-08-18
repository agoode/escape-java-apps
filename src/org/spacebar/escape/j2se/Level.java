package org.spacebar.escape.j2se;

import java.io.IOException;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.common.Bot;
import org.spacebar.escape.common.hash.FNV32;

public class Level extends org.spacebar.escape.common.Level {

    public Level(org.spacebar.escape.common.Level l) {
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
    	hash.fnv32(getPlayerX());
    	hash.fnv32(getPlayerY());
    
    	// tiles, oTiles
    	for (int i = 0; i < tiles.length; i++) {
    		hash.fnv32(tiles[i]);
    		hash.fnv32(oTiles[i]);
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

}
