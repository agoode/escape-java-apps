/*
 * Created on Dec 19, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.spacebar.escape.j2se;

import javax.sound.sampled.*;
import javax.sound.sampled.DataLine.Info;

import org.spacebar.escape.common.Effects;

/**
 * @author adam
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class NESEffects implements Effects {

    private static final Clip step = ResourceUtil.loadClip("nes/step.wav");

    private static final Clip broken = ResourceUtil.loadClip("nes/broken.wav");

    private static final Clip electricOff = ResourceUtil
            .loadClip("nes/electric-off.wav");

    private static final Clip exit = ResourceUtil.loadClip("nes/exit.wav");

    private static final Clip hole = ResourceUtil.loadClip("nes/hole.wav");

    private static final Clip laser = ResourceUtil.loadClip("nes/laser.wav");

    private static final Clip noStep = ResourceUtil
            .loadClip("nes/no-step.wav");

    private static final Clip pulse = ResourceUtil.loadClip("nes/pulse.wav");

    private static final Clip slide = ResourceUtil.loadClip("nes/slide.wav");

    private static final Clip swap = ResourceUtil.loadClip("nes/swap.wav");

    private static final Clip transport = ResourceUtil
            .loadClip("nes/transport.wav");

    private static final Clip zap = ResourceUtil.loadClip("nes/zap.wav");

    static {
        Mixer m = AudioSystem.getMixer(null);
        System.out.println("default mixer: " + m.getMixerInfo().getDescription());
        System.out.println(" max lines: " + m.getMaxLines(new Line.Info(DataLine.class)));
        
        Line.Info l[] = m.getSourceLineInfo(new Line.Info(DataLine.class));
        for (int j = 0; j < l.length; j++) {
            DataLine.Info dli = (Info) l[j];
            System.out.println(" " + dli);
            AudioFormat af[] = dli.getFormats();
            for (int k = 0; k < af.length; k++) {
                System.out.println("  " + af[k]);
            }
        }
        
        System.out.println();
        System.out.println("*** all mixers");
        
        Mixer.Info mi[] = AudioSystem.getMixerInfo();
        for (int i = 0; i < mi.length; i++) {
            Mixer m2 = AudioSystem.getMixer(mi[i]);
            System.out.println(mi[i].toString() + " " + mi[i].getDescription() + ", max lines: " + m2.getMaxLines(new Line.Info(Line.class)));
        }

        step.addLineListener(new NESLineListener("step"));
        laser.addLineListener(new NESLineListener("laser"));
        noStep.addLineListener(new NESLineListener("no step"));
    }

    private void rewindAndPlay(Clip c) {
        c.setFramePosition(0);
        c.start();
        System.out.println(System.currentTimeMillis() + ": playing "
                + c.getFormat());
    }

    public void doBroken() {
        rewindAndPlay(broken);
    }

    public void doElectricOff() {
        rewindAndPlay(electricOff);
    }

    public void doExit() {
        rewindAndPlay(exit);
    }

    public void doHole() {
        rewindAndPlay(hole);
    }

    public void doLaser() {
        rewindAndPlay(laser);
    }

    public void doNoStep() {
        rewindAndPlay(noStep);
    }

    public void doPulse() {
        rewindAndPlay(pulse);
    }

    public void doSlide() {
        rewindAndPlay(slide);
    }

    public void doStep() {
        rewindAndPlay(step);
    }

    public void doSwap() {
        rewindAndPlay(swap);
    }

    public void doTransport() {
        rewindAndPlay(transport);
    }

    public void doZap() {
        rewindAndPlay(zap);
    }

    public static class NESLineListener implements LineListener {
        private String name;

        public NESLineListener(String name) {
            this.name = name;
        }

        public void update(LineEvent event) {
            System.out.println(name + ": " + event.getType());
        }
    }
}