package com.teia.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TeiaUsersApplication {

	public static void main(String[] args) {
		SpringApplication.run(TeiaUsersApplication.class, args);
	}

}
