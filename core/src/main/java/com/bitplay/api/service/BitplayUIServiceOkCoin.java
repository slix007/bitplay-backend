package com.bitplay.api.service;

import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.api.domain.ob.FutureIndexJson;
import com.bitplay.api.domain.ob.LimitsJson;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexLimitsService;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.trade.UserTrades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Component("OkCoin")
public class BitplayUIServiceOkCoin extends AbstractBitplayUIService<OkCoinService> {

    private static final Logger logger = LoggerFactory.getLogger(BitplayUIServiceOkCoin.class);

    @Autowired
    private OkCoinService service;

    @Autowired
    private OkexLimitsService okexLimitsService;

    @Override
    public OkCoinService getBusinessService() {
        return service;
    }

    @Override
    public List<VisualTrade> fetchTrades() {
        final UserTrades trades = service.fetchMyTradeHistory();

        List<VisualTrade> askTrades = trades.getTrades().stream()
                .sorted((o1, o2) -> o1.getTimestamp().before(o2.getTimestamp()) ? 1 : -1)
                .map(this::toVisualTrade)
                .collect(Collectors.toList());
        return askTrades;
    }

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
        String details = null;
        try {

            final PlacingType placingSubType = PlacingType.valueOf(tradeRequestJson.getPlacementType().toString());
            Long tradeId = service.getArbitrageService().getLastInProgressTradeId();
            TradeResponse tradeResponse = service.placeOrder(new PlaceOrderArgs(orderType, amount, null, placingSubType,
                    signalType, 0, tradeId, signalType.getCounterName()));
            orderId = tradeResponse.getOrderId();
            details = tradeResponse.getErrorCode();

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
        return new FutureIndexJson(indexString, indexVal, sdf.format(timestamp), limitsJson, ethBtcBal, okexEstimatedDeliveryPrice);
    }

}
