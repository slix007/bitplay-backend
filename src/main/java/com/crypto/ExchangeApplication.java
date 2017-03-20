package com.crypto;

import com.crypto.quoine.QuoineBridge;
import com.crypto.quoine.QuoineSimpleExample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ExchangeApplication {

	public static void main(String[] args) {
//		SpringApplication.run(ExchangeApplication.class, args);

        final QuoineBridge quoineBridge = new QuoineBridge();
        quoineBridge.doTheWork();

        final QuoineSimpleExample quoineSimpleExample = new QuoineSimpleExample();
        quoineSimpleExample.doTheWork();

	}
}
