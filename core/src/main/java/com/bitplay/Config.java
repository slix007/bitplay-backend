package com.bitplay;

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

    @Value("${market.bitmex.key}")
    private String bitmexMarketKey;
    @Value("${market.bitmex.secret}")
    private String bitmexMarketSecret;

    @Value("${market.okex.key}")
    private String okexMarketKey;
    @Value("${market.okex.secret}")
    private String okexMarketSecret;

    @Value("${market.okex.ex.key:DEFAULT}")
    private String okexMarketExKey;
    @Value("${market.okex.ex.secret:DEFAULT}")
    private String okexMarketExSecret;
    @Value("${market.okex.ex.passphrase:DEFAULT}")
    private String okexMarketExPassphrase;

    @Value("${ui.password.trader}")
    private String uiPasswordForTrader;
    @Value("${ui.password.admin}")
    private String uiPasswordForAdmin;
    @Value("${ui.password.actuator}")
    private String uiPasswordForActuator;

//    private Integer eBestMin;
//    private BigDecimal coldStorage;
//
//    public void reload() {
//        Properties properties = reloadPropertyFile();
//        final String e_best_min = properties.getProperty("e_best_min");
//        final String cold_storage = properties.getProperty("cold_storage");
//        eBestMin = e_best_min == null ? null : Integer.valueOf(e_best_min);
//        coldStorage = cold_storage == null ? null : new BigDecimal(cold_storage);
//
//        log.info("Reload e_best_min=" + eBestMin + ", cold_storage=" + coldStorage);
//    }
//
//    private Properties reloadPropertyFile() {
//        Properties prop = new Properties();
//        try {
//            File jarPath = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
//            String propertiesPath = jarPath.getParentFile().getAbsolutePath();
//            if (propertiesPath.contains("file:")) {
//                // workaround1:
//                // /opt/bitplay/bitmex-okcoin/file:/opt/bitplay/bitmex-okcoin/bitplay.jar!/BOOT-INF
//                int i = propertiesPath.indexOf("file:");
//                propertiesPath = propertiesPath.substring(0, i - 1);
//            }
//            FileInputStream inStream;
//            try {
//                inStream = new FileInputStream(propertiesPath + "/application.properties");
//            } catch (FileNotFoundException e) {
//                // workaround for local development:
//                try {
//                    inStream = new FileInputStream(propertiesPath + "/classes/application.properties");
//                } catch (FileNotFoundException e1) {
//                    // workaround if others does not work
//                    inStream = new FileInputStream("/opt/bitplay/bitmex-okcoin/application.properties");
//                }
//            }
//            prop.load(inStream);
//            log.info(" propertiesPath-" + propertiesPath + ": " + prop.toString());
//            log.info(" e_best_min=" + prop.getProperty("e_best_min"));
//        } catch (IOException e1) {
//            log.error("Error reading properties", e1);
//        }
//        return prop;
//    }
}
