
package com.example.claudeshim;

import java.io.File;

public class BinaryLocator {

    public static String findRealClaude() {

        String path = System.getenv("PATH");
        if(path == null) return null;

        for(String dir : path.split(File.pathSeparator)){

            File f = new File(dir,"claude");

            if(f.exists() && f.canExecute())
                return f.getAbsolutePath();
        }

        return null;
    }
}
