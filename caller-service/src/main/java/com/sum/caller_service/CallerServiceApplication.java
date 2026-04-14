package com.sum.caller_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class CallerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CallerServiceApplication.class, args);
	}

}
