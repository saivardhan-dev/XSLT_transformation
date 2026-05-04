package com.pipeline.xslttransformer.config;

import jakarta.jms.ConnectionFactory;
import org.apache.camel.component.jms.JmsComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActiveMQConfig {

    // Camel JMS component explicitly wired with the ConnectionFactory
    // Spring Boot auto-configures ConnectionFactory from application.properties
    @Bean
    public JmsComponent jms(ConnectionFactory connectionFactory) {
        JmsComponent jmsComponent = new JmsComponent();
        jmsComponent.setConnectionFactory(connectionFactory);
        return jmsComponent;
    }
}