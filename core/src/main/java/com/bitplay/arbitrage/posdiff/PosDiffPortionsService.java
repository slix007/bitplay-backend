package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.NtUsdCheckEvent;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.ArbState;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.ConBoPortions;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PosDiffPortionsService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private PosDiffService posDiffService;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private SlackNotifications slackNotifications;

    /**
     * Runs on each pos change (bitmex on posDiffService event; okex each 200ms).
     */
    @Async("ntUsdSignalCheckExecutor")
    @EventListener(NtUsdCheckEvent.class)
    public void doCheck() {
        if (!arbitrageService.isFirstDeltasCalculated()) {
            return;
        }

        okCoinService.addOoExecutorTask(this::checkAndPlace);
    }

    private void checkAndPlace() {
        final Settings settings = settingsRepositoryService.getSettings();
        if (settings.getArbScheme() != ArbScheme.CON_B_O_PORTIONS) {
            // not portions signal
            return;
        }

        final PlaceOrderArgs currArgs = okCoinService.getPlaceOrderArgsRef().get();
        if (currArgs == null) {
            // no deferred order
            return;
        }
        if (!waitingToStart()) {
            return;
        }

        final ConBoPortions conBoPortions = settings.getConBoPortions();
        final StringBuilder logString = new StringBuilder();
        final BigDecimal filledUsdBlock;
        final boolean isBtmReady = bitmexService.getMarketState() == MarketState.READY && !bitmexService.hasOpenOrders();
        if (isBtmReady) {
            filledUsdBlock = getUsdBlockByBtmFilled(currArgs, logString);
        } else {
            filledUsdBlock = getBlockByNtUsd(currArgs, conBoPortions, logString);
        }

        if (filledUsdBlock == null) {
            return; // waiting for nt_usd while Bitmex is not READY
        }
        final BigDecimal maxBlockUsd = conBoPortions.getMaxPortionUsdOkex();
        final BigDecimal finalUsdBlock = useMaxBlockUsd(filledUsdBlock, maxBlockUsd);
        BigDecimal block = PlacingBlocks.toOkexCont(finalUsdBlock, okCoinService.getContractType().isEth());
        final BigDecimal argsAmount = currArgs.getAmount();
        block = argsAmount.compareTo(block) > 0 ? block : argsAmount;
        final PlaceOrderArgs placeArgs = currArgs.cloneAsPortion(block);
        final String nextPortion = placeArgs.getCounterNameWithPortion();
        final String ntUsdString = String.format("#%s %s. Okex: maxBlockUsd(%s), filledUsdBlock(%s), amountLeftCont(%s) => block(%s)",
                nextPortion,
                logString,
                maxBlockUsd, filledUsdBlock, argsAmount, block);
        log.info(ntUsdString);
        okCoinService.getTradeLogger().info(ntUsdString);
        if (block.signum() == 0) {
            resetIfBtmReady(filledUsdBlock, isBtmReady);
            return;
        }

        placeDeferredPortion(placeArgs, block);
    }

    private BigDecimal useMaxBlockUsd(BigDecimal filledUsdBlock, BigDecimal maxBlockUsd) {
        BigDecimal maxBlockCnt = PlacingBlocks.toOkexCont(maxBlockUsd, okCoinService.getContractType().isEth());
        if (maxBlockCnt.signum() <= 0) {
            // wrong settings
            final String msg = "wrong portions settings. maxBlockUsd=" + maxBlockUsd + "=> maxBlockCnt=" + maxBlockCnt;
            okCoinService.getTradeLogger().error(msg);
            warningLogger.info(msg);
            slackNotifications.sendNotify(NotifyType.SETTINGS_ERRORS, "msg");
        }
        return filledUsdBlock.compareTo(maxBlockUsd) <= 0 ? filledUsdBlock : maxBlockUsd;
    }

    private BigDecimal getUsdBlockByBtmFilled(PlaceOrderArgs currArgs, StringBuilder logString) {
        final BigDecimal btmFilled = bitmexService.getBtmFilled(currArgs);
        final BigDecimal cm = bitmexService.getCm();
        final BigDecimal filledUsd = PlacingBlocks.bitmexContToUsd(btmFilled, bitmexService.getContractType().isEth(), cm);
        logString.append(String.format("Bitmex READY. filledUsd = %s", filledUsd));
        return filledUsd;
    }

    private BigDecimal getBlockByNtUsd(PlaceOrderArgs currArgs, ConBoPortions conBoPortions, StringBuilder logString) {
        BigDecimal minToStart = conBoPortions.getMinNtUsdToStartOkex();
        BigDecimal ntUsd = posDiffService.getDcMainSet();
        if (minToStart.signum() <= 0) {
            // wrong settings
            final String msg = "wrong portions settings. minToStart=" + minToStart + ". Use 1usd.";
            minToStart = BigDecimal.ONE;
            warningLogger.info(msg);
            slackNotifications.sendNotify(NotifyType.SETTINGS_ERRORS, "msg");
        }
        if (ntUsd.signum() == 0) {
            // no start
            return null;
        }

        ntUsd = getNtUsdAbs(ntUsd, currArgs); // can be negative if manual order in opposite direction

        if (ntUsd.compareTo(minToStart) < 0) {
            // waiting
            return null;
        }

        logString.append(String.format("nt_usd(%s)>=min_to_start(%s).", ntUsd, minToStart));

        return ntUsd;
    }

    private boolean waitingToStart() {
        boolean waitingToStart = true;
        // check states Arb and Markets
        final MarketState marketState = okCoinService.getMarketState();
        if (marketState != MarketState.WAITING_ARB) {
            // wrong state (okex already started?)
            waitingToStart = marketState == MarketState.ARBITRAGE
                    && !okCoinService.hasOpenOrders()
                    && okCoinService.hasDeferredOrders();
        }
        return waitingToStart;
    }

    private BigDecimal getNtUsdAbs(BigDecimal ntUsd, PlaceOrderArgs currArgs) {
        final OrderType t = currArgs.getOrderType();
        final boolean b = t == OrderType.BID || t == OrderType.EXIT_ASK;
//        final boolean a = t == OrderType.ASK || t == OrderType.EXIT_BID;
        return b ? ntUsd.negate() : ntUsd;
    }

    private void resetIfBtmReady(BigDecimal filledUsdBlock, boolean btmReady) {
        final ArbState arbState = arbitrageService.getArbState();
        if (arbState != ArbState.IN_PROGRESS) {
            final String ntUsdString = String.format("WARNING: arbState(%s)", arbState);
            log.info(ntUsdString);
            okCoinService.getTradeLogger().info(ntUsdString);
            warningLogger.error(ntUsdString);
        }
        if (btmReady || arbState != ArbState.IN_PROGRESS) {
            okexReset(filledUsdBlock);
        }
    }

    private void okexReset(BigDecimal filledUsdBlock) {
        okCoinService.resetWaitingArb(filledUsdBlock.signum() > 0);
        arbitrageService.resetArbState(okCoinService.getCounterName(), "deferredPlacingPortion");
    }

    private void placeDeferredPortion(PlaceOrderArgs args, BigDecimal block) {
        okCoinService.beforeDeferredPlacing(args);
        okCoinService.placeOrder(args);
        okCoinService.changeDeferredAmountSubstract(block, args.getPortionsQty());
    }

}
