package com.nexus.nfv.sdn.onos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nfv.config.NexusProperties;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Selects the ONOS adapter explicitly from NEXUS_ONOS_MODE – never a silent fallback from real to demo. */
@Configuration
public class OnosConfig {

    @Bean
    OnosGateway onosGateway(NexusProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        if (props.onos().demo()) {
            LoggerFactory.getLogger(OnosConfig.class)
                    .warn("ONOS integration is in DEMO mode: SDN data is fixture data, no controller is contacted");
            return new DemoOnosGateway(mapper);
        }
        return new RestOnosGateway(props.onos(), builder);
    }
}
