package com.project2.ism;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class IsmApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(IsmApplication.class);
    }

    @Autowired
    private Environment env;

    @PostConstruct
    public void printSslConfig() {
        System.out.println("PORT = " + env.getProperty("server.port"));
        System.out.println("SSL keystore = " + env.getProperty("server.ssl.key-store"));
        System.out.println("SSL enabled = " + env.getProperty("server.ssl.enabled"));
        System.out.println("BOOT: Checking if SSL configuration is applied...");
    }

    @PostConstruct
    public void printConfigLocation() {
        System.out.println("CONFIG LOADED FROM = " +
                IsmApplication.class.getClassLoader().getResource("application.properties"));
    }
	public static void main(String[] args) {
		SpringApplication.run(IsmApplication.class, args);
	}

}
