package org.spacebar.escape.j2se;

import java.awt.Color;

import org.spacebar.escape.common.StyleStack;

public class StyleStack2 extends StyleStack {

    public Color getAWTColor() {
        int alpha;
        switch (this.alpha) {
        case ALPHA_100:
            alpha = 255;
            break;
        case ALPHA_50:
            alpha = 127;
            break;
        case ALPHA_25:
            alpha = 0;
            break;
        default:
            alpha = 255;
        }

        switch (color) {
        case COLOR_WHITE:
            return new Color(255, 255, 255, alpha);
        case COLOR_BLUE:
            return new Color(161, 162, 200, alpha);
        case COLOR_RED:
            return new Color(255, 117, 106, alpha);
        case COLOR_YELLOW:
            return new Color(255, 250, 106, alpha);
        case COLOR_GRAY:
            return new Color(159, 159, 159, alpha);
        case COLOR_GREEN:
            return new Color(106, 239, 90, alpha);
        default:
            return Color.BLACK;
        }
    }
}
