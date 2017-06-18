package com.bitplay.persistance.config;

import com.bitplay.persistance.repository.RepositoryPackage;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
@Configuration
@EnableMongoRepositories(basePackageClasses = RepositoryPackage.class)
//@ComponentScan(basePackageClasses=TemplatePackage.class)
public class MongoConfig extends AbstractMongoConfiguration {

    // ---------------------------------------------------- mongodb config

    @Override
    protected String getDatabaseName() {
        return "bitplay";
    }

    @Override
    @Bean
    public MongoClient mongo() throws Exception {
        MongoClient client = new MongoClient("localhost");
        client.setWriteConcern(WriteConcern.ACKNOWLEDGED);
        return client;
    }

//    @Override
//    protected String getMappingBasePackage() {
//        return "com.lishman.springdata.domain";
//    }

    // ---------------------------------------------------- MongoTemplate

    @Bean
    public MongoTemplate mongoTemplate() throws Exception {
        return new MongoTemplate(mongo(), getDatabaseName());
    }

}