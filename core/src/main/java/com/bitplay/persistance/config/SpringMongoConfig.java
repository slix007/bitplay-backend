package com.bitplay.persistance.config;

import com.bitplay.persistance.repository.RepositoryPackage;
import com.github.mongobee.Mongobee;
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
    private static final String DBNAME = "bitplay";
    private static final String HOST_WITH_PORT = "localhost:26459";

    // ---------------------------------------------------- mongodb config

//    @Bean(name="mongoAudit")
//    public AuditorAware<String> mongoAuditProvider() {
//        return new DefaultAuditor();
//    }

    @Override
    protected String getDatabaseName() {
        return DBNAME;
    }

    @Override
    @Bean
    public MongoClient mongo() {
        MongoClientOptions.Builder clientOptions = new MongoClientOptions.Builder();
        clientOptions.minConnectionsPerHost(100);//min
        clientOptions.connectionsPerHost(100);//max
        clientOptions.writeConcern(WriteConcern.ACKNOWLEDGED);

        return new MongoClient(HOST_WITH_PORT, clientOptions.build());
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

    @Bean
    public Mongobee mongobee() {
        Mongobee runner = new Mongobee(String.format("mongodb://%s/%s", HOST_WITH_PORT, DBNAME));
        runner.setDbName(DBNAME);         // host must be set if not set in URI
        runner.setChangeLogsScanPackage("com.bitplay.persistance.migration.changelogs"); // the package to be scanned for changesets

        return runner;
    }
}