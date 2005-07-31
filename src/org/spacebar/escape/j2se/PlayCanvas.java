/*
 * Created on Apr 1, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.spacebar.escape.j2se;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.spacebar.escape.common.*;

/**
 * @author agoode
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class PlayCanvas extends LevelCanvas {

    Solution solution = new Solution();

    Solution playbackSolution;

    public PlayCanvas(Level theLevel, Continuation c) {
        super(theLevel, c);
        origLevel = new Level(theLevel);

        setupKeys();
    }

    final Level origLevel;

    private void setupKeys() {
        // moving
        addAction("LEFT", "goLeft", new Mover(Entity.DIR_LEFT));
        addAction("DOWN", "goDown", new Mover(Entity.DIR_DOWN));
        addAction("RIGHT", "goRight", new Mover(Entity.DIR_RIGHT));
        addAction("UP", "goUp", new Mover(Entity.DIR_UP));

        // scaling
        addAction("CLOSE_BRACKET", "scaleUp", new Scaler(false));
        addAction("OPEN_BRACKET", "scaleDown", new Scaler(true));

        // bizarro
        addAction("Y", "toggleAlt", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                swapWithBizarro();
                bufferRepaint();
            }
        });

        // restart
        final Action a = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                reset();
                if (playbackSolution != null) {
                    playSolution();
                }
            }
        };
        addAction("ENTER", "restart", a);
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                a.actionPerformed(new ActionEvent(e.getSource(),
                        ActionEvent.ACTION_PERFORMED, "restart"));
            }
        });

        // quit
        addAction("ESCAPE", "exit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                theWayOut.invoke();
            }
        });
    }

    protected void playSolution() {
        playbackSolution.verify(theLevel, 100, System.out);
        bufferRepaint();
    }

    protected void reset() {
        setLevel(new Level(origLevel));
        solution = new Solution();
    }

    private class Mover extends AbstractAction {
        final int dir;

        public Mover(int dir) {
            this.dir = dir;
        }

        public void actionPerformed(ActionEvent e) {
            if (playbackSolution != null) {
                return;
            }

            if (theLevel.move(dir, effects)) {
                // append to solution
                solution.addToSolution(dir);
            }
            if (theLevel.isDead()) {
                effects.doLaser();
                status = Characters.RED + "You died!" + Characters.POP;

                // XXX: race condition
                new Thread() {
                    public void run() {
                        if (Message.quick(PlayCanvas.this, "You've died.",
                                -Characters.FONT_HEIGHT * 8, "Try again",
                                "Quit", Characters.PICS + Characters.SKULLICON)) {
                            reset();
                        } else {
                            theWayOut.invoke();
                        }
                    }
                }.start();
            } else if (theLevel.isWon()) {
                effects.doExit();
                status = Characters.GREEN + "Solved!" + Characters.POP;
                System.out.println("won in " + solution.length() + " steps");
                System.out.println(solution);

                // XXX: race condition
                new Thread() {
                    public void run() {
                        Message.quick(PlayCanvas.this, "You solved it!!",
                                -Characters.FONT_HEIGHT * 8, "Continue", null,
                                Characters.PICS + Characters.THUMBICON);
                        theWayOut.invoke();
                    }
                }.start();
            }
        }
    }

    private class Scaler extends AbstractAction {
        final boolean smaller;

        public Scaler(boolean smaller) {
            this.smaller = smaller;
        }

        public void actionPerformed(ActionEvent e) {
            if (smaller) {
                setRelativeScale(-1);
            } else {
                setRelativeScale(+1);
            }

            //            System.out.println("scale: " + scale + ", scaleVal: " +
            // scaleVal);

            bufferRepaint();
        }
    }

    public void setSolution(Solution s) {
        // kill the normal interaction, replace with one thing
        playbackSolution = s;
    }
}
