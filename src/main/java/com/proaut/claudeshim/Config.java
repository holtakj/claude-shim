package com.proaut.claudeshim;

public class Config {

    public String https_proxy;
    public String http_proxy;
    public String no_proxy;
    public Boolean disable_telemetry;
    public String log_file;

    @Override
    public String toString() {
        return "Config{" +
                "https_proxy='" + https_proxy + '\'' +
                ", http_proxy='" + http_proxy + '\'' +
                ", no_proxy='" + no_proxy + '\'' +
                ", disable_telemetry=" + disable_telemetry +
                ", log_file='" + log_file + '\'' +
                '}';
    }
}
