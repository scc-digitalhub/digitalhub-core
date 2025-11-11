/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.components.cloud.config;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;

@Configuration
@Order(8)
@ConditionalOnProperty(name = "event-queue.services.rabbit.enabled", havingValue = "true", matchIfMissing = false)
@Import(RabbitAutoConfiguration.class)
public class RabbitMQConfig {

    @Value("${event-queue.services.rabbit.queue-name}")
    private String QUEUE_NAME;

    @Value("${event-queue.services.rabbit.entity-topic}")
    private String ENTITY_TOPIC;

    @Value("${event-queue.services.rabbit.entity-routing-key}")
    private String ENTITY_ROUTING_KEY;

    @Value("${event-queue.services.rabbit.connection.host}")
    private String HOST;

    @Value("${event-queue.services.rabbit.connection.port}")
    private Integer PORT;

    @Value("${event-queue.services.rabbit.connection.username}")
    private String USERNAME;

    @Value("${event-queue.services.rabbit.connection.password}")
    private String PASSWORD;

    @Value("${event-queue.services.rabbit.connection.virtual-host}")
    private String VIRTUAL_HOST;

    @Bean
    public Queue myQueue() {
        // Set the durable option here
        return new Queue(QUEUE_NAME, true, false, false, Map.of("x-queue-type", "quorum"));
    }

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(ENTITY_TOPIC, true, false);
    }

    @Bean
    public Binding binding(Queue myQueue, TopicExchange topicExchange) {
        //return BindingBuilder.bind(myQueue).to(myExchange).with("myRoutingKey");
        return BindingBuilder.bind(myQueue).to(topicExchange).with(ENTITY_ROUTING_KEY);
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(HOST);
        connectionFactory.setPort(PORT);
        connectionFactory.setUsername(USERNAME);
        connectionFactory.setPassword(PASSWORD);
        connectionFactory.setVirtualHost(VIRTUAL_HOST);

        return connectionFactory;
    }
}
