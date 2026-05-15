package com.proaut.claudeshim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Config {

    public String https_proxy;
    public String http_proxy;
    public String no_proxy;
    public Boolean disable_telemetry;
    public Map<String, List<String>> envPaths = new LinkedHashMap<>();

    @Override
    public String toString() {
        return "Config{" +
                "https_proxy='" + https_proxy + '\'' +
                ", http_proxy='" + http_proxy + '\'' +
                ", no_proxy='" + no_proxy + '\'' +
                ", disable_telemetry=" + disable_telemetry +
                ", envPaths=" + envPaths +
                '}';
    }
}
