package io.github.snower.jaslock.spring.example.pubsub.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    public Queue queue() {
        return new Queue("pubsub", true, false, false);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange("pubsub");
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(exchange()).withQueueName();
    }
}
