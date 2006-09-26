package org.spacebar.escape.pdf;

import org.spacebar.escape.common.Level;

public abstract class TileCondition {
    public abstract boolean test(Level l, int x, int y);

    static public TileCondition createTileMatchCondition(byte[] tiles) {
        final byte myTiles[] = new byte[tiles.length];
        System.arraycopy(tiles, 0, myTiles, 0, tiles.length);

        TileCondition t = new TileCondition() {
            @Override
            public boolean test(Level l, int x, int y) {
                byte tile = l.tileAt(x, y);
                for (int i = 0; i < myTiles.length; i++) {
                    if (tile == myTiles[i]) {
                        return true;
                    }
                }
                return false;
            }
        };

        return t;
    }

    static public TileCondition createTileMatchCondition(final byte tile) {
        TileCondition t = new TileCondition() {
            @Override
            public boolean test(Level l, int x, int y) {
                return tile == l.tileAt(x, y);
            }
        };

        return t;
    }
    static public TileCondition createTileMatchCondition(final byte tile,
            final boolean hasEntity) {
        TileCondition t = new TileCondition() {
            @Override
            public boolean test(Level l, int x, int y) {
                return tile == l.tileAt(x, y) && hasEntity == l.isEntityAt(x, y);
            }
        };

        return t;
    }
}
