package com.bitplay.arbitrage.posdiff;

import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.model.ArbState;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.DealPricesRepositoryService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.fluent.dealprices.DealPrices;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.ConBoPortions;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.NtUsdCheckEvent;
import com.bitplay.xchange.dto.Order.OrderType;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
    private SlackNotifications slackNotifications;

    @Autowired
    private DealPricesRepositoryService dealPricesRepositoryService;

    @Autowired
    private TradeService tradeService;

    /**
     * Runs on each pos change (bitmex on posDiffService event; okex each 200ms).
     */
    @Async("ntUsdSignalCheckExecutor")
    @EventListener(NtUsdCheckEvent.class)
    public void doCheck() {
        if (!arbitrageService.isFirstDeltasCalculated()) {
            return;
        }

        arbitrageService.getRightMarketService().addOoExecutorTask(this::checkAndPlace);
    }

    private void checkAndPlace() {
        if (arbitrageService.getRightMarketService().getMarketStaticData() == MarketStaticData.BITMEX) { // always false
            return;
        }
        final OkCoinService okCoinService = (OkCoinService) arbitrageService.getRightMarketService();
        PlaceOrderArgs currArgs = okCoinService.getPlaceOrderArgsRef().get();
        if (currArgs == null) {
            // no deferred order
            return;
        }
        if (currArgs.getArbScheme() != ArbScheme.R_wait_L_portions) {
            // not portions signal
            return;
        }
        if (!waitingToStart(currArgs.getCounterNameWithPortion())) {
            return;
        }

        final ConBoPortions conBoPortions = settingsRepositoryService.getSettings().getConBoPortions();
        final StringBuilder logString = new StringBuilder();
        final BigDecimal leftFilledUsd;
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final boolean isBtmReady = left.getMarketState() == MarketState.READY && !left.hasOpenOrders();
        if (isBtmReady) {
            leftFilledUsd = getUsdBlockByLeftFilled(currArgs, logString);
            currArgs = resetFullAmount(currArgs, leftFilledUsd);
            if (currArgs == null) { // how we end then?
                return;
            }
        } else {
            leftFilledUsd = getBlockByNtUsd(currArgs, conBoPortions, logString);
        }

        if (leftFilledUsd == null) {
            return; // waiting for nt_usd while Bitmex is not READY
        }
        final BigDecimal maxPortionUsd = conBoPortions.getMaxPortionUsdOkex();
        final BigDecimal finalUsdBlock = useMaxBlockUsd(leftFilledUsd, maxPortionUsd);
        BigDecimal block = PlacingBlocks.toOkexCont(finalUsdBlock, okCoinService.getContractType().isQuanto());
        final BigDecimal argsAmount = currArgs.getAmount();
        block = argsAmount.compareTo(block) > 0 ? block : argsAmount;
        final PlaceOrderArgs placeArgs = currArgs.cloneAsPortion(block);
        final String nextPortion = placeArgs.getCounterNameWithPortion();
        final String ntUsdString = String.format("#%s %s, leftFilledUsd(%s). Right: maxPortionUsd(%s),  amountLeftCont(%s) => portion_block(%s)",
                nextPortion,
                logString, leftFilledUsd,
                maxPortionUsd, argsAmount, block);
        log.info(ntUsdString);
        okCoinService.getTradeLogger().info(ntUsdString);
        if (block.signum() == 0) {
            resetIfBtmReady(leftFilledUsd, isBtmReady);
            return;
        }

        placeDeferredPortion(placeArgs, block);
    }

    private BigDecimal useMaxBlockUsd(BigDecimal filledUsdBlock, BigDecimal maxBlockUsd) {
        final OkCoinService okCoinService = (OkCoinService) arbitrageService.getRightMarketService();
        BigDecimal maxBlockCnt = PlacingBlocks.toOkexCont(maxBlockUsd, okCoinService.getContractType().isQuanto());
        if (maxBlockCnt.signum() <= 0) {
            // wrong settings
            final String msg = "wrong portions settings. maxBlockUsd=" + maxBlockUsd + "=> maxBlockCnt=" + maxBlockCnt;
            okCoinService.getTradeLogger().error(msg);
            warningLogger.info(msg);
            slackNotifications.sendNotify(NotifyType.SETTINGS_ERRORS, "msg");
        }
        return filledUsdBlock.compareTo(maxBlockUsd) <= 0 ? filledUsdBlock : maxBlockUsd;
    }

    private BigDecimal getUsdBlockByLeftFilled(PlaceOrderArgs currArgs, StringBuilder logString) {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final BigDecimal leftFilled = left.getLeftFilledAndUpdateBPriceFact(currArgs, true);
        final BigDecimal cm = arbitrageService.getCm();
        final BigDecimal filledUsd = left.isBtm()
                ? PlacingBlocks.bitmexContToUsd(leftFilled, left.getContractType().isQuanto(), cm)
                : PlacingBlocks.okexContToUsd(leftFilled, left.getContractType().isQuanto());
        logString.append(left.getNameWithType()).append(" READY.");
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

    /**
     * Sets WAITING_ARB when ARBITRAGE+noOO or any other status.
     * (It goes in the same thread as place/move and after check for deferredOrder)
     */
    private boolean waitingToStart(String counterNameWithPortion) {
        final MarketServicePreliq okexService = arbitrageService.getRightMarketService();

        boolean waitingToStart = true;
        // check states Arb and Markets
        final MarketState marketState = okexService.getMarketState();
        if (marketState != MarketState.WAITING_ARB && marketState != MarketState.STARTING_VERT) {
            // wrong state (okex already started?)
            final boolean hasOpenOrders = okexService.hasOpenOrders();
            if (marketState == MarketState.ARBITRAGE) {
                waitingToStart = !hasOpenOrders;
                if (waitingToStart) {
                    final String warn = "ARBITRAGE>WAITING_ARB, because hasOpenOrders=false and hasDeferredOrders=true";
                    log.warn(warn);
                    okexService.getTradeLogger().warn(warn);
                    okexService.setMarketState(MarketState.WAITING_ARB, counterNameWithPortion);
                }
            } else {
                waitingToStart = true;
                final String warn = String.format("%s>WAITING_ARB, because hasDeferredOrders=true", marketState);
                log.warn(warn);
                okexService.getTradeLogger().warn(warn);
                okexService.setMarketState(MarketState.WAITING_ARB, counterNameWithPortion);
            }
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
        final OkCoinService okexService = (OkCoinService) arbitrageService.getRightMarketService();
        final ArbState arbState = arbitrageService.getArbState();
        if (arbState != ArbState.IN_PROGRESS) {
            final String ntUsdString = String.format("WARNING: arbState(%s)", arbState);
            log.info(ntUsdString);
            okexService.getTradeLogger().info(ntUsdString);
            warningLogger.error(ntUsdString);
        }
        if (btmReady || arbState != ArbState.IN_PROGRESS) {
            okexService.resetWaitingArb("posDiffPortionsService:btmReady", filledUsdBlock.signum() > 0);
            arbitrageService.resetArbState("posDiffPortionsService:btmReady");
        }
    }

    private void placeDeferredPortion(PlaceOrderArgs args, BigDecimal block) {
        final OkCoinService okexService = (OkCoinService) arbitrageService.getRightMarketService();
        okexService.beforeDeferredPlacing(args);
        final PlaceOrderArgs changedOrder = okexService.changeDeferredAmountSubstract(block, args.getPortionsQty());
        if (changedOrder != null) {
            final String msg = "still has deferred. Deferred order not full. Before: " + args + " After:" + changedOrder;
            log.info(msg);
            okexService.getTradeLogger().info(msg);
        }

        okexService.placeOrder(args);
    }

    private PlaceOrderArgs resetFullAmount(PlaceOrderArgs currArgs, BigDecimal btmFilledUsd) {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final OkCoinService okexService = (OkCoinService) arbitrageService.getRightMarketService();
        PlaceOrderArgs updatedArgs = currArgs;
        final Long tradeId = currArgs.getTradeId();
        final DealPrices dealPrices = dealPricesRepositoryService.findByTradeId(tradeId);
        final boolean isQuanto = left.getContractType().isQuanto();

        final boolean leftOkex = left.getMarketStaticData() == MarketStaticData.OKEX;
        final BigDecimal btmFilledCont = PlacingBlocks.toBitmexContPure(btmFilledUsd, isQuanto, arbitrageService.getCm(), leftOkex);

        // when btmFilled < fullAmount
        if (btmFilledCont.compareTo(dealPrices.getBPriceFact().getFullAmount()) < 0) {
            final BigDecimal okexNewFullAmount = PlacingBlocks.toOkexCont(btmFilledUsd, isQuanto);
            if (currArgs.getFullAmount().compareTo(okexNewFullAmount) != 0) {
                updatedArgs = okexService.updateFullDeferredAmount(okexNewFullAmount);
            }
            dealPricesRepositoryService.updateFactPriceFullAmount(tradeId, btmFilledCont, okexNewFullAmount);
            // print delta logs
            final DealPrices updatedDp = dealPricesRepositoryService.findByTradeId(tradeId);
            tradeService.info(tradeId, currArgs.getCounterName(),
                    String.format("Reset right amount(left %s FILL). plan: L_block=%s, R_block=%s, final_blocks: L_block=%s, R_block=%s",
                            btmFilledCont.signum() > 0 ? "PARTIAL" : "ZERO",
                            updatedDp.getBBlock(),
                            updatedDp.getOBlock(),
                            updatedDp.getBPriceFact().getFullAmount(),
                            updatedDp.getOPriceFact().getFullAmount()
                    ));
        }
        return updatedArgs;
    }
}
