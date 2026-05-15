package com.proaut.claudeshim;

import java.util.LinkedHashMap;
import java.util.Map;

public class Environment {

    public final String name;
    public final Config config;
    public final Map<String, String> extraEnvVars;

    public Environment(String name, Config config, Map<String, String> extraEnvVars) {
        this.name = name;
        this.config = config;
        this.extraEnvVars = extraEnvVars != null ? extraEnvVars : new LinkedHashMap<>();
    }
}
