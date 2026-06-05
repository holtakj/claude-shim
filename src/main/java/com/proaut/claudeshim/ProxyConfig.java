package com.proaut.claudeshim;

/**
 * Holds proxy and telemetry configuration.
 * <p>
 * This record encapsulates all proxy-related settings and telemetry control
 * for the Claude Shim application.
 */
public record ProxyConfig(String httpsProxy, String httpProxy, String noProxy, Boolean disableTelemetry) {

    /**
     * Creates an empty ProxyConfig with all values set to null.
     *
     * @return a ProxyConfig with null values
     */
    public static ProxyConfig empty() {
        return new ProxyConfig(null, null, null, null);
    }

    /**
     * Creates a ProxyConfig with only HTTPS proxy configured.
     *
     * @param httpsProxy the HTTPS proxy URL
     * @return a ProxyConfig with the specified HTTPS proxy
     */
    public static ProxyConfig httpsOnly(String httpsProxy) {
        return new ProxyConfig(httpsProxy, null, null, null);
    }

    /**
     * Creates a ProxyConfig with both HTTP and HTTPS proxies configured.
     *
     * @param httpsProxy the HTTPS proxy URL
     * @param httpProxy  the HTTP proxy URL
     * @return a ProxyConfig with the specified proxies
     */
    public static ProxyConfig withHttpAndHttps(String httpsProxy, String httpProxy) {
        return new ProxyConfig(httpsProxy, httpProxy, null, null);
    }

    /**
     * Creates a ProxyConfig with telemetry disabled.
     *
     * @param disableTelemetry whether to disable telemetry
     * @return a ProxyConfig with telemetry disabled
     */
    public static ProxyConfig withTelemetryDisabled(boolean disableTelemetry) {
        return new ProxyConfig(null, null, null, disableTelemetry);
    }

    /**
     * Returns a new ProxyConfig with the HTTPS proxy updated.
     *
     * @param httpsProxy the new HTTPS proxy URL
     * @return a new ProxyConfig with the updated HTTPS proxy
     */
    public ProxyConfig withHttpsProxy(String httpsProxy) {
        return new ProxyConfig(httpsProxy, httpProxy, noProxy, disableTelemetry);
    }

    /**
     * Returns a new ProxyConfig with the HTTP proxy updated.
     *
     * @param httpProxy the new HTTP proxy URL
     * @return a new ProxyConfig with the updated HTTP proxy
     */
    public ProxyConfig withHttpProxy(String httpProxy) {
        return new ProxyConfig(httpsProxy, httpProxy, noProxy, disableTelemetry);
    }

    /**
     * Returns a new ProxyConfig with the no_proxy list updated.
     *
     * @param noProxy the new no_proxy list
     * @return a new ProxyConfig with the updated no_proxy list
     */
    public ProxyConfig withNoProxy(String noProxy) {
        return new ProxyConfig(httpsProxy, httpProxy, noProxy, disableTelemetry);
    }

    /**
     * Returns a new ProxyConfig with telemetry setting updated.
     *
     * @param disableTelemetry whether to disable telemetry
     * @return a new ProxyConfig with the updated telemetry setting
     */
    public ProxyConfig withDisableTelemetry(Boolean disableTelemetry) {
        return new ProxyConfig(httpsProxy, httpProxy, noProxy, disableTelemetry);
    }

    /**
     * Checks if this ProxyConfig has any proxy settings configured.
     *
     * @return true if any proxy or telemetry setting is configured
     */
    public boolean hasConfig() {
        return httpsProxy != null || httpProxy != null || noProxy != null || disableTelemetry != null;
    }

    @Override
    public String toString() {
        return "ProxyConfig{" +
            "httpsProxy='" + maskPasswordInUrl(httpsProxy) + '\'' +
            ", httpProxy='" + maskPasswordInUrl(httpProxy) + '\'' +
            ", noProxy='" + noProxy + '\'' +
            ", disableTelemetry=" + disableTelemetry +
            '}';
    }

    private static String maskPasswordInUrl(String url) {
        if (url == null) {
            return null;
        }
        int protoIdx = url.indexOf("://");
        if (protoIdx != -1) {
            int startUserinfo = protoIdx + 3;
            int atIdx = url.indexOf('@', startUserinfo);
            if (atIdx != -1) {
                String userinfo = url.substring(startUserinfo, atIdx);
                int colonIdx = userinfo.indexOf(':');
                if (colonIdx != -1 && colonIdx < userinfo.length() - 1) {
                    String username = userinfo.substring(0, colonIdx);
                    return username + ":*****" + url.substring(atIdx);
                }
            }
        }
        return url;
    }
}
