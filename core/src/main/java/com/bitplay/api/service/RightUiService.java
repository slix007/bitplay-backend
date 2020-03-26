package com.bitplay.api.service;

import com.bitplay.api.dto.LeverageRequest;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.TradeRequestJson;
import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.settings.PlacingType;
import lombok.RequiredArgsConstructor;
import org.knowm.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Component
@RequiredArgsConstructor
public class RightUiService extends AbstractUiService<MarketServicePreliq> {

    private static final Logger logger = LoggerFactory.getLogger(RightUiService.class);

    private final ArbitrageService arbitrageService;

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

    public ResultJson changeLeverage(LeverageRequest leverageRequest, ArbType arbType) {
        final OkCoinService service = arbType == ArbType.LEFT
                ? (OkCoinService) arbitrageService.getLeftMarketService()
                : (OkCoinService) arbitrageService.getRightMarketService();
        final String resDescr = service.changeOkexLeverage(leverageRequest.getLeverage());
        return new ResultJson("", resDescr);
    }
}
