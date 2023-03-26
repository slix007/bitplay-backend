package com.bitplay;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.posdiff.PosDiffService;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexSettlementService;
import com.bitplay.persistance.CumPersistenceService;
import com.bitplay.persistance.DealPricesRepositoryService;
import com.bitplay.persistance.LastPriceDeviationService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.settings.ContractMode;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.Settings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Slf4j
@Component
@DependsOn("mongobee")
@RequiredArgsConstructor
public class TwoMarketStarter {

//    private Config config;
//    private ApplicationContext context;

    private volatile MarketServicePreliq leftMarketService;
    private volatile MarketServicePreliq rightMarketService;
    private volatile PosDiffService posDiffService;

//    private SlackNotifications slackNotifications;
//    private ArbitrageService arbitrageService;
//    private RestartService restartService;
//    private SettingsRepositoryService settingsRepositoryService;

    private final Config config;
    private final ApplicationContext context;

    private final ArbitrageService arbitrageService;
    private final RestartService restartService;

    private final SlackNotifications slackNotifications;
    private final LastPriceDeviationService lastPriceDeviationService;
    private final TradeService fplayTradeService;
    private final PersistenceService persistenceService;
    private final SettingsRepositoryService settingsRepositoryService;
    private final OrderRepositoryService orderRepositoryService;
    private final CumPersistenceService cumPersistenceService;
    private final OkexSettlementService okexSettlementService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final DealPricesRepositoryService dealPricesRepositoryService;
    private final MonitoringDataService monitoringDataService;


//    public TwoMarketStarter(ApplicationContext context,
//                            Config config) {
//        this.context = context;
//        this.config = config;
//    }

    //    @Autowired
//    public void setSlackNotifications(SlackNotifications slackNotifications) {
//        this.slackNotifications = slackNotifications;
//    }
//
//    @Autowired
//    public void setArbitrageService(ArbitrageService arbitrageService) {
//        this.arbitrageService = arbitrageService;
//    }
//
//    @Autowired
//    public void setRestartService(RestartService restartService) {
//        this.restartService = restartService;
//    }
//
//    @Autowired
//    public void setSettingsRepositoryService(SettingsRepositoryService settingsRepositoryService) {
//        this.settingsRepositoryService = settingsRepositoryService;
//    }
//
//    @EventListener(ApplicationReadyEvent.class)
    @PostConstruct
    public void init() {

        final ExecutorService startExecutor = Executors.newFixedThreadPool(2,
                new ThreadFactoryBuilder().setNameFormat("starting-thread-%d").build());


        restartService.scheduleCheckForFullStart();

        final Settings settings = settingsRepositoryService.getSettings();
        final ContractMode contractMode = settings.getContractMode();
        final ContractType leftContractType = contractMode.getLeft();
        final ContractType rightContractType = contractMode.getRight();

        CompletableFuture<MarketServicePreliq> left = CompletableFuture.supplyAsync(() -> initMarket(leftContractType, ArbType.LEFT), startExecutor);
        CompletableFuture<MarketServicePreliq> right = CompletableFuture.supplyAsync(() -> initMarket(rightContractType, ArbType.RIGHT), startExecutor);

        left.thenCombine(right, (leftMarket, rightMarket) -> {
            leftMarketService = leftMarket;
            rightMarketService = rightMarket;
            return leftMarket != null && rightMarket != null;
        }).thenAccept((started) -> {
            if (started) {
                try {
                    final String correctPosition = "pos-diff";
                    posDiffService = (PosDiffService) context.getBean(correctPosition);
                    log.info("PosDiffService: " + posDiffService);
                    if (leftMarketService != null && rightMarketService != null) {
                        posDiffService.init();
                    }
                    arbitrageService.init(this);
                } catch (Exception e) {
                    log.error("Initialization error", e);
                }
            }

        });
    }

    private MarketServicePreliq initMarket(ContractType contractType, ArbType arbType) {
        MarketServicePreliq marketService = null;
        final String marketName = contractType.getMarketName();
        try {
            marketService = (MarketServicePreliq) context.getBean(marketName);
            if (marketName.equals(BitmexService.NAME)) {
                marketService.init(
                        config.getBitmexMarketKey(),
                        config.getBitmexMarketSecret(),
                        contractType,
                        config.getBitmexMarketUrl(),
                        config.getBitmexMarketHost(),
                        config.getBitmexMarketPort(),
                        config.getBitmexMarketWssUrl(),
                        config.getBitmexMarketWssUrl(),
                        arbType);
            } else if (marketName.equals(OkCoinService.NAME)) {
                if (arbType == ArbType.LEFT) {
                    marketService.init(config.getOkexMarketKey(), config.getOkexMarketSecret(), contractType,
                            config.getOkexMarketUrl(),
                            config.getOkexMarketHost(),
                            config.getOkexMarketPort(),
                            config.getOkexMarketWssUrlPublic(),
                            config.getOkexMarketWssUrlPrivate(),
                            arbType,
                            config.getOkexLeftMarketExKey(),
                            config.getOkexLeftMarketExSecret(),
                            config.getOkexLeftMarketExPassphrase(),
                            config.getOkexLeftMarketV5Key(),
                            config.getOkexLeftMarketV5Secret(),
                            config.getOkexLeftMarketV5Passphrase()
                    );
                } else {
                    marketService.init(config.getOkexMarketKey(), config.getOkexMarketSecret(), contractType,
                            config.getOkexMarketUrl(),
                            config.getOkexMarketHost(),
                            config.getOkexMarketPort(),
                            config.getOkexMarketWssUrlPublic(),
                            config.getOkexMarketWssUrlPrivate(),
                            arbType,
                            config.getOkexMarketExKey(),
                            config.getOkexMarketExSecret(),
                            config.getOkexMarketExPassphrase(),
                            config.getOkexMarketV5Key(),
                            config.getOkexMarketV5Secret(),
                            config.getOkexMarketV5Passphrase());
                }
            }

        } catch (Exception e) {
            log.error("MARKET Initialization error. Set STOPPED.", e);
            // Workaround to make work the other market
            if (marketService != null) {
                marketService.setEmptyPos();
            }
            arbitrageService.setArbStateStopped();
            slackNotifications.sendNotify(NotifyType.STOPPED, marketName + " STOPPED: Initialization error.");
        }
        return marketService;
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
