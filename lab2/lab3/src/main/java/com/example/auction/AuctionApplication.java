package com.example.auction;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
@SpringBootApplication
public class AuctionApplication {
 public static void main(String[] args) { SpringApplication.run(AuctionApplication.class,args); }
 @Bean public Clock clock() { return Clock.systemUTC(); }
}
