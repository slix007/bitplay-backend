package com.bitplay.persistance.config;

import com.bitplay.persistance.repository.RepositoryPackage;
import com.github.mongobee.Mongobee;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.WriteConcern;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDbFactory;
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
@Slf4j
@Configuration
@EnableMongoRepositories(basePackageClasses = RepositoryPackage.class)
@EnableMongoAuditing
public class SpringMongoConfig extends AbstractMongoConfiguration {
    private static final String DBNAME = "bitplay";
    private static final String HOST_WITH_PORT = "localhost:26459";

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

    @Override
    protected Collection<String> getMappingBasePackages() {
        return Stream.of("com.bitplay.persistance.domain").collect(Collectors.toList());
    }

    @Bean
    public MongoTemplate mongoTemplate() throws Exception {
        final MongoDbFactory mongoDbFactory = mongoDbFactory();
        MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(mongoDbFactory), new MongoMappingContext());
        converter.setCustomConversions(MongoCustomConversions.customConversions());
        converter.afterPropertiesSet();
        return new MongoTemplate(mongoDbFactory, converter);
    }

    @Bean(name = "mongobee")
    public Mongobee mongobee() throws Exception {
        Mongobee runner = new Mongobee(String.format("mongodb://%s/%s", HOST_WITH_PORT, DBNAME));
        runner.setDbName(DBNAME);         // host must be set if not set in URI
        runner.setChangeLogsScanPackage("com.bitplay.persistance.migration.changelogs"); // the package to be scanned for changesets
        runner.setMongoTemplate(mongoTemplate());

        return runner;
    }
}
