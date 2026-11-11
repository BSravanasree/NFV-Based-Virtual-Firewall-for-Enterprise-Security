package com.nexus.nfv;

import com.nexus.nfv.config.NexusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// JWT is the only authentication mechanism: no default in-memory user / generated password
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(NexusProperties.class)
public class NexusNfvApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusNfvApplication.class, args);
    }
}
