package com.johnny.udpserver.service;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class RabbitServiceTest {
    @RabbitListener(queues = "myQueue")
    public void rabbitTest(Message message) {
        String s = new String(message.getBody());
        System.out.println("message.getBody() = " + s);
        System.out.println("message.getMessageProperties() = " + message.getMessageProperties());
    }
}
