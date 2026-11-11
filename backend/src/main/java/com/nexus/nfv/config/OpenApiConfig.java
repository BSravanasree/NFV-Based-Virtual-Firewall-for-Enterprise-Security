package com.nexus.nfv.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI nexusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NEXUS NFV – Enterprise Virtual Firewall Management API")
                        .version("v1")
                        .description("""
                                Control-plane API for deploying and operating NFV firewall VNFs.
                                Integrations: OSM NBI (orchestration), ONOS REST (SDN/OpenFlow),
                                NETCONF (configuration), firewall VNF agent (nftables).
                                All infrastructure-backed responses carry a `dataSource` of LIVE, LAB or DEMO.""")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
