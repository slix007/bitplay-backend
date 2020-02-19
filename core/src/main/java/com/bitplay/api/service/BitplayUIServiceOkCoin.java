package com.bitplay.api.service;

import com.bitplay.api.dto.LeverageRequest;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.TradeRequestJson;
import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.api.dto.ob.FutureIndexJson;
import com.bitplay.api.dto.ob.LimitsJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.LimitsService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.model.SwapSettlement;
import com.bitplay.persistance.domain.settings.PlacingType;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Component("OkCoin")
public class BitplayUIServiceOkCoin extends AbstractBitplayUIService<MarketServicePreliq> {

    private static final Logger logger = LoggerFactory.getLogger(BitplayUIServiceOkCoin.class);

    @Autowired
    private ArbitrageService arbitrageService;

    @Override
    public MarketServicePreliq getBusinessService() {
        return arbitrageService.getRightMarketService();
    }

    @SuppressWarnings("Duplicates")
    public TradeResponseJson doTrade(TradeRequestJson tradeRequestJson) {
        final BigDecimal amount = new BigDecimal(tradeRequestJson.getAmount());
        Order.OrderType orderType;
        SignalType signalType;
        switch (tradeRequestJson.getType()) {
            case BUY:
                orderType = Order.OrderType.BID;
                signalType = SignalType.MANUAL_BUY;
                break;
            case SELL:
                orderType = Order.OrderType.ASK;
                signalType = SignalType.MANUAL_SELL;
                break;
            default:
//                throw new IllegalArgumentException("No such order type " + tradeRequestJson.getType());
                return new TradeResponseJson("Wrong orderType", "Wrong orderType");
        }

        String orderId = null;
        String details;
        try {

            final PlacingType placingSubType = PlacingType.valueOf(tradeRequestJson.getPlacementType().toString());
            final Long tradeId = arbitrageService.getLastInProgressTradeId();
            final PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                    .orderType(orderType)
                    .amount(amount)
                    .placingType(placingSubType)
                    .signalType(signalType)
                    .attempt(1)
                    .tradeId(tradeId)
                    .counterName(signalType.getCounterName())
                    .build();
            final TradeResponseJson r = arbitrageService.getRightMarketService().placeWithPortions(placeOrderArgs, tradeRequestJson.getPortionsQty());
            orderId = r.getOrderId();
            details = (String) r.getDetails();

        } catch (Exception e) {
            getBusinessService().getTradeLogger().error("Place taker order error " + e.toString());
            logger.error("Place taker order error", e);
            details = e.getMessage();
        } finally {
//            if (getBusinessService().isBusy()) {
//                getBusinessService().getEventBus().send(BtsEvent.MARKET_FREE);
//            }
        }

        return new TradeResponseJson(orderId, details);
    }

    public FutureIndexJson getFutureIndex() {
        final OkCoinService service = (OkCoinService) arbitrageService.getRightMarketService();
        final LimitsService okexLimitsService = service.getLimitsService();

        final ContractIndex contractIndex = getBusinessService().getContractIndex();
        final String indexVal = contractIndex.getIndexPrice().toPlainString();
        final BigDecimal markPrice = service.getMarkPrice();

        final String indexString = String.format("%s/%s (1c=%sbtc)",
                indexVal,
                markPrice,
                getBusinessService().calcBtcInContract());
        final Date timestamp = contractIndex.getTimestamp();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        final LimitsJson limitsJson = okexLimitsService.getLimitsJson();

        // bid[1] в token trading (okex spot).
        String ethBtcBal = service.getEthBtcTicker() == null ? ""
                : "Quote ETH/BTC: " + service.getEthBtcTicker().getBid().toPlainString();

        final String okexEstimatedDeliveryPrice = service.getForecastPrice().toPlainString();
        final SwapSettlement swapSettlement = service.getSwapSettlement();
        return new FutureIndexJson(indexString, indexVal, sdf.format(timestamp), limitsJson, ethBtcBal, okexEstimatedDeliveryPrice, swapSettlement);
    }

    public ResultJson changeLeverage(LeverageRequest leverageRequest) {
        final OkCoinService service = (OkCoinService) arbitrageService.getRightMarketService();
        final String resDescr = service.changeOkexLeverage(leverageRequest.getLeverage());
        return new ResultJson("", resDescr);
    }
}
