package com.rag.lecturelens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LecturelensApplication {

	public static void main(String[] args) {
		SpringApplication.run(LecturelensApplication.class, args);
	}

}
