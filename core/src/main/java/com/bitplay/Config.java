package com.bitplay;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Properties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${cold_storage}")
    private BigDecimal defaultColdStorage;

    private Integer eBestMin;
    private BigDecimal coldStorage;

    public void reload() {
        Properties properties = reloadPropertyFile();
        final String e_best_min = properties.getProperty("e_best_min");
        final String cold_storage = properties.getProperty("cold_storage");
        eBestMin = e_best_min == null ? null : Integer.valueOf(e_best_min);
        coldStorage = cold_storage == null ? null : new BigDecimal(cold_storage);

        log.info("Reload e_best_min=" + eBestMin + ", cold_storage=" + coldStorage);
    }

    private Properties reloadPropertyFile() {
        Properties prop = new Properties();
        try {
            File jarPath = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
            String propertiesPath = jarPath.getParentFile().getAbsolutePath();
            if (propertiesPath.contains("file:")) {
                // workaround1:
                // /opt/bitplay/bitmex-okcoin/file:/opt/bitplay/bitmex-okcoin/bitplay.jar!/BOOT-INF
                int i = propertiesPath.indexOf("file:");
                propertiesPath = propertiesPath.substring(0, i - 1);
            }
            FileInputStream inStream;
            try {
                inStream = new FileInputStream(propertiesPath + "/application.properties");
            } catch (FileNotFoundException e) {
                // workaround for local development:
                try {
                    inStream = new FileInputStream(propertiesPath + "/classes/application.properties");
                } catch (FileNotFoundException e1) {
                    // workaround if others does not work
                    inStream = new FileInputStream("/opt/bitplay/bitmex-okcoin/application.properties");
                }
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
        return eBestMin != null ? eBestMin : defaultEBestMin;
    }

    public BigDecimal getColdStorage() {
        return coldStorage != null ? coldStorage : defaultColdStorage;
    }
}
