package com.agentpluginhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentPluginHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPluginHubApplication.class, args);
    }
}
