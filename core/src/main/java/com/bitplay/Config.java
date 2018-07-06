package com.bitplay;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    @Value("${e_best_min}")
    private Integer defaultEBestMin;

    //    @Value("${e_best_min}")
    private Integer eBestMin;

    @Scheduled(fixedRate = 2000)
    public void reload() {
        Properties properties = reloadPropertyFile();
        String e_best_min = properties.getProperty("e_best_min");
        eBestMin = e_best_min == null ? null : Integer.valueOf(e_best_min);
    }

    private Properties reloadPropertyFile() {
        Properties prop = new Properties();
        try {
            File jarPath = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
            String propertiesPath = jarPath.getParentFile().getAbsolutePath();
            if (propertiesPath.contains("file:")) {
                // /opt/bitplay/bitmex-okcoin/file:/opt/bitplay/bitmex-okcoin/bitplay.jar!/BOOT-INF
                int i = propertiesPath.indexOf("file:");
                propertiesPath = propertiesPath.substring(0, i - 1);
            }
            FileInputStream inStream;
            try {
                inStream = new FileInputStream(propertiesPath + "/application.properties");
            } catch (FileNotFoundException e) {
                // workaround for local development:
                inStream = new FileInputStream(propertiesPath + "/classes/application.properties");
            }
            prop.load(inStream);
            log.info(" propertiesPath-" + propertiesPath + ": " + prop.toString());
            log.info(" e_best_min=" + prop.getProperty("e_best_min"));
        } catch (IOException e1) {
            log.error("Error reading properties", e1);
        }
        return prop;
    }

    public Integer getEBestMin() {
        if (eBestMin != null) {
            return eBestMin;
        }
        return defaultEBestMin;
    }

}
