package ch.alpian.payment.infrastructure.rail;

import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import ch.alpian.payment.domain.SettlementRequest;
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
        return new SettlementRail() {
            @Override
            public SettlementOutcome settle(SettlementRequest request) {
                return request.receiver().isInternal() ? internal.settle(request) : external.settle(request);
            }

            @Override
            public SettlementOutcome getStatus(SettlementRequest request) {
                return request.receiver().isInternal() ? internal.getStatus(request) : external.getStatus(request);
            }
        };
    }
}
