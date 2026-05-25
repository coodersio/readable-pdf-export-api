package io.cooders.readablepdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReadablePdfApiApplication {

    private static final Logger log = LoggerFactory.getLogger(ReadablePdfApiApplication.class);

    public static void main(String[] args) {
        log.info("Starting Readable PDF API...");
        SpringApplication.run(ReadablePdfApiApplication.class, args);
        log.info("Readable PDF API started.");
    }
}

