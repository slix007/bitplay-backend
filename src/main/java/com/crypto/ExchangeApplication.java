package com.crypto;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExchangeApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {

//		SpringApplication.run(ExchangeApplication.class, args);

        new ExchangeApplication()
                .configure(new SpringApplicationBuilder(ExchangeApplication.class))
                .run(args);


//        final QuoineBridge quoineBridge = new QuoineBridge();
//        quoineBridge.doTheWork();

//        final QuoineSimpleExample quoineSimpleExample = new QuoineSimpleExample();
//        quoineSimpleExample.doTheWork();

//
//        final PoloniexExample poloniexExample = new PoloniexExample();
//        poloniexExample.doWork();




    }
}
