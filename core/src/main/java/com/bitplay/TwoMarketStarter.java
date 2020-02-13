package com.bitplay;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.posdiff.PosDiffService;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.ContractMode;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.Settings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Component
@DependsOn("mongobee")
public class TwoMarketStarter {

    private final static Logger logger = LoggerFactory.getLogger(TwoMarketStarter.class);

    private Config config;
    private ApplicationContext context;

    private MarketServicePreliq leftMarketService;
    private MarketServicePreliq rightMarketService;
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

    @EventListener(ApplicationReadyEvent.class)
    public void init() {

        final ExecutorService startExecutor = Executors.newFixedThreadPool(2,
                new ThreadFactoryBuilder().setNameFormat("starting-thread-%d").build());


        restartService.scheduleCheckForFullStart();

        final Settings settings = settingsRepositoryService.getSettings();
        final ContractMode contractMode = settings.getContractMode();
        final ContractType leftContractType = contractMode.getLeft();
        final ContractType rightContractType = contractMode.getRight();

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> {
            try {
                final String firstMarketName = config.getLeftMarketName();
                leftMarketService = (MarketServicePreliq) context.getBean(firstMarketName);
                leftMarketService.init(config.getLeftMarketKey(), config.getLeftMarketKey(), leftContractType,
                        config.getLeftMarketExKey(),
                        config.getLeftMarketExSecret(),
                        config.getLeftMarketExPassphrase());

                logger.info("MARKET_LEFT: " + leftMarketService);
                return true;
            } catch (Exception e) {
                logger.error("MARKET_LEFT Initialization error. Set STOPPED.", e);
                // Workaround to make work the other market
                leftMarketService.setEmptyPos();
                arbitrageService.setArbStateStopped();
                slackNotifications.sendNotify(NotifyType.STOPPED, BitmexService.NAME + " STOPPED: Initialization error.");
                return false;
            }
        }, startExecutor);

        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> {
            try {
                final String secondMarketName = config.getRightMarketName();
                rightMarketService = (MarketServicePreliq) context.getBean(secondMarketName);
                rightMarketService.init(config.getRightMarketKey(), config.getRightMarketSecret(), rightContractType,
                        config.getRightMarketExKey(),
                        config.getRightMarketExSecret(),
                        config.getRightMarketExPassphrase());
                logger.info("MARKET_RIGHT: " + rightMarketService);
                return true;
            } catch (Exception e) {
                logger.error("MARKET_RIGHT Initialization error. Set STOPPED.", e);
                // Workaround to make work the other market
                rightMarketService.setEmptyPos();
                arbitrageService.setArbStateStopped();
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

    public MarketServicePreliq getLeftMarketService() {
        return leftMarketService;
    }

    public MarketServicePreliq getRightMarketService() {
        return rightMarketService;
    }

    public PosDiffService getPosDiffService() {
        return posDiffService;
    }


}
