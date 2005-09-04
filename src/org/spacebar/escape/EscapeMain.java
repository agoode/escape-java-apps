/*
 * Created on Dec 14, 2004
 */
package org.spacebar.escape;

import java.io.*;

import org.spacebar.escape.common.BitInputStream;
import org.spacebar.escape.j2se.EscapeFrame;
import org.spacebar.escape.solver.Level;

public class EscapeMain {

    public EscapeMain(File f) {
        FileInputStream fis;
        Level level = null;
        try {
            fis = new FileInputStream(f);
            level = new Level(new BitInputStream(fis));
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        new EscapeFrame(level);
    }

    public static void main(String[] args) {
        new EscapeMain(new File(args[0]));
    }
}