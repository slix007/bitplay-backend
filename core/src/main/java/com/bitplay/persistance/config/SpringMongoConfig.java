package com.bitplay.persistance.config;

import com.bitplay.persistance.repository.RepositoryPackage;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.WriteConcern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
@Configuration
@EnableMongoRepositories(basePackageClasses = RepositoryPackage.class)
@EnableMongoAuditing
//@ComponentScan(basePackageClasses=TemplatePackage.class)
public class SpringMongoConfig extends AbstractMongoConfiguration {

    // ---------------------------------------------------- mongodb config

    @Override
    protected String getDatabaseName() {
        return "bitplay";
    }

    @Override
    @Bean
    public MongoClient mongo() throws Exception {
        MongoClientOptions.Builder clientOptions = new MongoClientOptions.Builder();
        clientOptions.minConnectionsPerHost(100);//min
        clientOptions.connectionsPerHost(100);//max
        clientOptions.writeConcern(WriteConcern.ACKNOWLEDGED);

        return new MongoClient("localhost:26459", clientOptions.build());
    }

//    @Override
//    protected String getMappingBasePackage() {
//        return "com.lishman.springdata.domain";
//    }

    // ---------------------------------------------------- MongoTemplate
/*
    class DateToZonedDateTimeConverter implements Converter {
        @Override
        public Object convert(Object o, Class aClass, Object o1) {
            final Date source = (Date) o;
            return source == null ? null : ZonedDateTime.ofInstant(source.toInstant(), ZoneId.systemDefault());
        }
    }

    class ZonedDateTimeToDateConverter implements Converter {
        @Override
        public Object convert(Object o, Class aClass, Object o1) {
            final ZonedDateTime source = (ZonedDateTime) o;
            return source == null ? null : Date.from(source.toInstant());
        }
    }

    @Bean
    public CustomConversions customConversions() {
        List<Converter> converters = new ArrayList<>();
        converters.add(new DateToZonedDateTimeConverter());
        converters.add(new ZonedDateTimeToDateConverter());
        return new CustomConversions(converters);
    }*/
    @Bean
    public MongoTemplate mongoTemplate() throws Exception {
        MappingMongoConverter converter = new MappingMongoConverter(
                new DefaultDbRefResolver(mongoDbFactory()), new MongoMappingContext());
//        converter.setCustomConversions(customConversions());
        converter.afterPropertiesSet();
        return new MongoTemplate(mongoDbFactory(), converter);

//        return new MongoTemplate(mongo(), getDatabaseName());
    }

}