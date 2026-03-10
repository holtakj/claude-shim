
package com.example.claudeshim;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Instant;

public class Logger {

    private static PrintWriter writer;

    public static void init(String file) {
        if (file == null) return;

        try {
            writer = new PrintWriter(new FileWriter(expand(file), true), true);
        } catch(Exception ignored) {}
    }

    public static void log(String level, String msg) {

        if(writer == null) return;

        writer.println("{\"ts\":\""+Instant.now()+"\",\"level\":\""+level+"\",\"msg\":\""+msg+"\"}");
    }

    private static String expand(String p){

        if(p.startsWith("~"))
            return System.getProperty("user.home") + p.substring(1);

        return p;
    }
}
