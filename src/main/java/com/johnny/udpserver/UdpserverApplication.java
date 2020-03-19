package com.johnny.udpserver;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@EnableRabbit
@SpringBootApplication
public class UdpserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(UdpserverApplication.class, args);
    }

}
