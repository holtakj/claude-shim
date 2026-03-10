
package com.example.claudeshim;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public class ClaudeShim {

    public static void main(String[] args) throws Exception {

        boolean debug = Arrays.asList(args).contains("--shim-debug");

        Config cfg = loadConfig();

        Logger.init(cfg.log_file);

        String real = BinaryLocator.findRealClaude();

        Logger.log("INFO","real claude: "+real);

        if(debug){
            System.out.println("Claude shim debug");
            System.out.println("Binary: "+real);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(real);

        for(String a:args)
            if(!a.equals("--shim-debug"))
                cmd.add(a);

        ProcessBuilder pb = new ProcessBuilder(cmd);

        Map<String,String> env = pb.environment();

        if(cfg.https_proxy != null)
            env.put("HTTPS_PROXY",cfg.https_proxy);

        if(Boolean.TRUE.equals(cfg.disable_telemetry)){
            env.put("DO_NOT_TRACK","1");
            env.put("CLAUDE_DISABLE_TELEMETRY","1");
        }

        pb.inheritIO();

        Process p = pb.start();
        System.exit(p.waitFor());
    }

    private static Config loadConfig(){

        try{

            Path p = Paths.get(System.getProperty("user.home"),
                    ".config","claude-shim","config.yaml");

            if(Files.exists(p)){

                InputStream in = Files.newInputStream(p);
                Yaml y = new Yaml();

                return y.loadAs(in,Config.class);
            }

        }catch(Exception ignored){}

        return new Config();
    }
}
