package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.account.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OkexPreliqService {

    protected static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private OkexTradeLogger tradeLogger;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private PersistenceService persistenceService;

//    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
//            new ThreadFactoryBuilder().setNameFormat("okex-preliq-thread-%d").build());
//
//    @EventListener(ApplicationReadyEvent.class)
//    public void init() {
//        scheduler.scheduleWithFixedDelay(() -> {
//            try {
//                checkForDecreasePosition();
//            } catch (Exception e) {
//                log.error("Error on checkForDecreasePosition", e);
//            }
//        }, 30, 1, TimeUnit.SECONDS);
//    }
//
//    protected final DelayTimer dtPreliq = new DelayTimer();


//    public void checkForDecreasePosition() {
//        Instant start = Instant.now();
//
//        if (okCoinService.isMarketStopped()) {
//            dtPreliq.stop();
//            return;
//        }
//        Position position = okCoinService.getPosition();
//        LiqInfo liqInfo = okCoinService.getLiqInfo();
//
//        final BigDecimal oDQLCloseMin = persistenceService.fetchGuiLiqParams().getODQLCloseMin();
//        final BigDecimal pos = position.getPositionLong().subtract(position.getPositionShort());
//        final CorrParams corrParams = persistenceService.fetchCorrParams();
//
//        if (liqInfo.getDqlCurr() != null
//                && liqInfo.getDqlCurr().compareTo(BigDecimal.valueOf(-30)) > 0 // workaround when DQL is less zero
//                && liqInfo.getDqlCurr().compareTo(oDQLCloseMin) < 0
//                && pos.signum() != 0
//                && corrParams.getPreliq().hasSpareAttempts()) {
//
//            arbitrageService.setArbStatePreliq();
//            dtPreliq.activate();
//
//            final Integer delaySec = persistenceService.getSettingsRepositoryService().getSettings().getPosAdjustment().getPreliqDelaySec();
//            long secToReady = dtPreliq.secToReady(delaySec);
//            if (secToReady > 0) {
//                String msg = "O_PRE_LIQ signal mainSet. Waiting delay(sec)=" + secToReady;
//                log.info(msg);
//                warningLogger.info(msg);
//                tradeLogger.info(msg);
//            } else {
//                final String counterForLogs = okCoinService.getCounterName();
//                String msg = String.format("#%s O_PRE_LIQ starting: p(%s-%s)/dql%s/dqlClose%s",
//                        counterForLogs,
//                        position.getPositionLong().toPlainString(), position.getPositionShort().toPlainString(),
//                        liqInfo.getDqlCurr().toPlainString(), oDQLCloseMin.toPlainString());
//                log.info(msg);
//                warningLogger.info(msg);
//                tradeLogger.info(msg);
//                final BestQuotes bestQuotes = Utils.createBestQuotes(
//                        arbitrageService.getSecondMarketService().getOrderBook(),
//                        arbitrageService.getFirstMarketService().getOrderBook());
//                if (pos.signum() > 0) {
//                    arbitrageService.startPreliqOnDelta2(SignalType.O_PRE_LIQ, bestQuotes);
//                } else if (pos.signum() < 0) {
//                    arbitrageService.startPreliqOnDelta1(SignalType.O_PRE_LIQ, bestQuotes);
//                }
//                dtPreliq.stop();
//            }
//        } else {
//            dtPreliq.stop();
//        }
//        Instant end = Instant.now();
//        Utils.logIfLong(start, end, log, "checkForDecreasePosition");
//    }

}
