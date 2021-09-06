package com.ttech.tvoip.coma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CoturnHealthcheckerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoturnHealthcheckerApplication.class, args);
		
	}

}
