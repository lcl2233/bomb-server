package com.bomb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bomb.debug")
public class DebugProperties {

    private boolean enabled = false;
}
