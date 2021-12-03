package com.example.notice;


import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public SimpleMessageListenerContainer messageListenerContainer(ConnectionFactory connectionFactory) {
        SimpleMessageListenerContainer container =
                new SimpleMessageListenerContainer(connectionFactory);
        //是否收发都是用同一个channel，如果是true的话，接收消息和发送消息是用同一个channel，如果是false，发消息时是用一个新的channel
        container.setExposeListenerChannel(true);
        //设置自定义监听器
        container.setMessageListener(new NoticeListener());

        return container;
    }

}
