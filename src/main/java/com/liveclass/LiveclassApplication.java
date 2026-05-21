package com.liveclass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
public class LiveclassApplication {

	public static void main(String[] args) {
		SpringApplication.run(LiveclassApplication.class, args);
	}

}
