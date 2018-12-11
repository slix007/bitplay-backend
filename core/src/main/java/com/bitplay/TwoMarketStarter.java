package com.bitplay;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketState;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.ContractMode;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.domain.settings.Settings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service
public class TwoMarketStarter {

    private final static Logger logger = LoggerFactory.getLogger(TwoMarketStarter.class);

    private Config config;
    private ApplicationContext context;

    private MarketServicePreliq firstMarketService;
    private MarketServicePreliq secondMarketService;
    private PosDiffService posDiffService;

    private SlackNotifications slackNotifications;
    private ArbitrageService arbitrageService;
    private RestartService restartService;
    private SettingsRepositoryService settingsRepositoryService;
    public TwoMarketStarter(ApplicationContext context,
                            Config config) {
        this.context = context;
        this.config = config;
    }

    @Autowired
    public void setSlackNotifications(SlackNotifications slackNotifications) {
        this.slackNotifications = slackNotifications;
    }

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    @Autowired
    public void setRestartService(RestartService restartService) {
        this.restartService = restartService;
    }

    @Autowired
    public void setSettingsRepositoryService(SettingsRepositoryService settingsRepositoryService) {
        this.settingsRepositoryService = settingsRepositoryService;
    }

    @PostConstruct
    private void init() {

        final ExecutorService startExecutor = Executors.newFixedThreadPool(2,
                new ThreadFactoryBuilder().setNameFormat("starting-thread-%d").build());


        restartService.scheduleCheckForFullStart();

        final Settings settings = settingsRepositoryService.getSettings();
        final ContractMode contractMode = settings.getContractMode();
        final OkexContractType okexContractType = contractMode.getOkexContractType();
        final BitmexContractType bitmexContractType = contractMode.getBitmexContractType();

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> {
            try {
                final String firstMarketName = config.getFirstMarketName();
                firstMarketService = (MarketServicePreliq) context.getBean(firstMarketName);
                firstMarketService.init(config.getFirstMarketKey(), config.getFirstMarketSecret(), bitmexContractType);
                logger.info("MARKET1: " + firstMarketService);
                return true;
            } catch (Exception e) {
                logger.error("MARKET1 Initialization error. Set STOPPED.", e);
                // Workaround to make work the other market
                firstMarketService.getPosition().setPositionLong(BigDecimal.ZERO);
                firstMarketService.getPosition().setPositionShort(BigDecimal.ZERO);
                firstMarketService.setMarketState(MarketState.STOPPED);
                slackNotifications.sendNotify(NotifyType.STOPPED, BitmexService.NAME + " STOPPED: Initialization error.");
                return false;
            }
        }, startExecutor);

        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> {
            try {
                final String secondMarketName = config.getSecondMarketName();
                secondMarketService = (MarketServicePreliq) context.getBean(secondMarketName);
                secondMarketService.init(config.getSecondMarketKey(), config.getSecondMarketSecret(), okexContractType);
                logger.info("MARKET2: " + secondMarketService);
                return true;
            } catch (Exception e) {
                logger.error("MARKET2 Initialization error. Set STOPPED.", e);
                // Workaround to make work the other market
                secondMarketService.getPosition().setPositionLong(BigDecimal.ZERO);
                secondMarketService.getPosition().setPositionShort(BigDecimal.ZERO);
                secondMarketService.setMarketState(MarketState.STOPPED);
                slackNotifications.sendNotify(NotifyType.STOPPED, OkCoinService.NAME + " STOPPED: Initialization error.");
                return false;
            }
        }, startExecutor);

        first.thenCombine(second, (firstIsDone, secondIsDone) -> firstIsDone && secondIsDone)
                .thenRun(() -> {
                    try {
                        final String correctPosition = "pos-diff";
                        posDiffService = (PosDiffService) context.getBean(correctPosition);
                        logger.info("PosDiffService: " + posDiffService);
                        arbitrageService.init(this);
                    } catch (Exception e) {
                        logger.error("Initialization error", e);
                    }

                });
    }

    public MarketServicePreliq getFirstMarketService() {
        return firstMarketService;
    }

    public MarketServicePreliq getSecondMarketService() {
        return secondMarketService;
    }

    public PosDiffService getPosDiffService() {
        return posDiffService;
    }
}
