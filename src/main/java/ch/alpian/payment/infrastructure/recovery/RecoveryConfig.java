package ch.alpian.payment.infrastructure.recovery;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RecoveryProperties.class)
class RecoveryConfig {}
