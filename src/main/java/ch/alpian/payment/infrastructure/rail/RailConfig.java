package ch.alpian.payment.infrastructure.rail;

import ch.alpian.payment.domain.SettlementRail;
import java.security.SecureRandom;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the single {@link SettlementRail} the application sees: it routes by receiver type. */
@Configuration
@EnableConfigurationProperties(SimulatedRailProperties.class)
class RailConfig {

    @Bean
    SettlementRail settlementRail(JdbcClient jdbc, SimulatedRailProperties properties) {
        var internal = new InternalTransferRail();
        var external = new SimulatedExternalRail(jdbc, properties, new SecureRandom());
        return request -> request.receiver().isInternal() ? internal.settle(request) : external.settle(request);
    }
}
