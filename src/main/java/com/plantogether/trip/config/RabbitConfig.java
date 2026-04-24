package com.plantogether.trip.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

  public static final String EXCHANGE = "plantogether.events";
  public static final String ROUTING_KEY_TRIP_CREATED = "trip.created";
  public static final String ROUTING_KEY_MEMBER_JOINED = "trip.member.joined";
  public static final String ROUTING_KEY_POLL_LOCKED = "poll.locked";
  public static final String QUEUE_POLL_LOCKED = "trip.poll-locked.queue";

  @Bean
  public TopicExchange plantogetherExchange() {
    return new TopicExchange(EXCHANGE);
  }

  @Bean
  public Queue pollLockedQueue() {
    return QueueBuilder.durable(QUEUE_POLL_LOCKED).build();
  }

  @Bean
  public Binding pollLockedBinding(Queue pollLockedQueue, TopicExchange plantogetherExchange) {
    return BindingBuilder.bind(pollLockedQueue)
        .to(plantogetherExchange)
        .with(ROUTING_KEY_POLL_LOCKED);
  }

  @Bean
  public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(cf);
    template.setMessageConverter(converter);
    return template;
  }
}
