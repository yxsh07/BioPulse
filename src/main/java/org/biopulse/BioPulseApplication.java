package org.biopulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BioPulseApplication {
  public static void main(String[] args) {
    SpringApplication.run(BioPulseApplication.class, args);
  }
}
