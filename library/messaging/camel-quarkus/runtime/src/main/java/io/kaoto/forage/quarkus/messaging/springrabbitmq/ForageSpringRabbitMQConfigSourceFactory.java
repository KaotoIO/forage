package io.kaoto.forage.quarkus.messaging.springrabbitmq;

import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.ForageQuarkusConfigSourceAdapter;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConfig;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQModuleDescriptor;

public class ForageSpringRabbitMQConfigSourceFactory extends ForageQuarkusConfigSourceAdapter<SpringRabbitMQConfig> {

    @Override
    protected ForageModuleDescriptor<SpringRabbitMQConfig, ?> descriptor() {
        return new SpringRabbitMQModuleDescriptor();
    }
}
