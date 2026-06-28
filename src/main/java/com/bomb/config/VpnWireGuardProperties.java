package com.bomb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "vpn.wireguard")
public class VpnWireGuardProperties {

    private boolean enabled = false;
    private String host;
    private int port = 22;
    private String username = "root";
    private String privateKeyPath;
    private String knownHostsPath;
    private String scriptPath = "/etc/wireguard/add-client.sh";
    private int connectTimeoutMs = 15000;
    private int commandTimeoutMs = 60000;
}
