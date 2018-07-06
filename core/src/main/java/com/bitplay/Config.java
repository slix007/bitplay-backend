package com.bitplay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Component
@Getter
public class Config {

    @Value("${market.first}")
    private String firstMarketName;
    @Value("${market.first.key}")
    private String firstMarketKey;
    @Value("${market.first.secret}")
    private String firstMarketSecret;

    @Value("${market.second}")
    private String secondMarketName;
    @Value("${market.second.key}")
    private String secondMarketKey;
    @Value("${market.second.secret}")
    private String secondMarketSecret;

    @Value("${deltas-series.enabled}")
    private Boolean deltasSeriesEnabled;

    @Autowired
    private StandardEnvironment environment;

    @Scheduled(fixedRate = 1000)
    public void reload() throws IOException {
        MutablePropertySources propertySources = environment.getPropertySources();
        Properties properties = new Properties();
        InputStream inputStream = getClass().getResourceAsStream("/application.properties");
        properties.load(inputStream);
        inputStream.close();

        replace(propertySources, properties, "applicationConfig: [classpath:/application.properties]");

        replace(propertySources, properties, "devtools-local");
    }

    private void replace(MutablePropertySources sourceList, Properties newProperties, String sourceName) {
        PropertiesPropertySource newPropertySource = new PropertiesPropertySource(sourceName, newProperties);
        if (sourceList.get(sourceName) == null) {
            sourceList.addLast(newPropertySource);
        } else {
            sourceList.replace(sourceName, newPropertySource);
        }
    }

    public Integer getEBestMin() {
        String e_best_min = environment.getProperty("e_best_min");
        return e_best_min == null ? null : Integer.valueOf(e_best_min);
    }

}
