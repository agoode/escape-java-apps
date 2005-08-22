package org.spacebar.escape;

import java.io.*;

import org.spacebar.escape.common.LevelPack;

public class PackLevels {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: " + PackLevels.class.getName()
                    + " outLevel.pack inLevel1.esx [inLevel2.esx] ...");
            System.exit(1);
        }

        try {
            OutputStream out = new FileOutputStream(args[0]);
            InputStream ins[] = new FileInputStream[args.length - 1];

            for (int i = 1; i < args.length; i++) {
                System.out.print(args[i] + " ");
                System.out.flush();
                ins[i-1] = new FileInputStream(args[i]);
            }

            System.out.print("-> " + args[0]);
            LevelPack.pack(ins, out);
            System.out.println();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
