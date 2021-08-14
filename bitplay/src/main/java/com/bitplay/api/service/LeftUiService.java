package com.bitplay.api.service;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.bitmex.BitmexTimeService;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.api.dto.ChangeRequestJson;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.TradeRequestJson;
import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.model.Pos;
import lombok.RequiredArgsConstructor;
import com.bitplay.xchange.dto.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Component("LeftUi")
@RequiredArgsConstructor
public class LeftUiService extends AbstractUiService<MarketServicePreliq> {

    private final ArbitrageService arbitrageService;
    private final BitmexTimeService bitmexTimeService;

    @Override
    public BitmexTimeService getBitmexTimeService() {
        return bitmexTimeService;
    }

    @Override
    public MarketServicePreliq getBusinessService() {
        return arbitrageService.getLeftMarketService();
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
                return new TradeResponseJson("Wrong orderType", "Wrong orderType");
//                throw new IllegalArgumentException("No such order type " + tradeRequestJson.getType());
        }

        final PlacingType placingType = PlacingType.valueOf(tradeRequestJson.getPlacementType().toString());

        final Long tradeId = arbitrageService.getLastInProgressTradeId();
        final String toolName = tradeRequestJson.getToolName();
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        if (left.isBtm() && toolName != null && toolName.equals("XBTUSD")) {
            final String counterName = left.getCounterName(signalType, tradeId);
            final PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                    .orderType(orderType)
                    .amount(amount)
                    .placingType(placingType)
                    .signalType(signalType)
                    .attempt(1)
                    .tradeId(tradeId)
                    .counterName(counterName)
                    .contractType(BitmexContractType.XBTUSD_Perpetual)
                    .amountType(tradeRequestJson.getAmountType())
                    .build();
            final TradeResponse tradeResponse = left.placeOrder(placeOrderArgs);
            return new TradeResponseJson(tradeResponse.getOrderId(), tradeResponse.getErrorCode());
        }
        // Portions for mainSet orders
        final PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                .orderType(orderType)
                .amount(amount)
                .placingType(placingType)
                .signalType(signalType)
                .attempt(1)
                .tradeId(tradeId)
                .counterName(signalType.getCounterName())
                .build();

        return left.placeWithPortions(placeOrderArgs, tradeRequestJson.getPortionsQty());
    }

    public ResultJson setCustomSwapTime(ChangeRequestJson customSwapTime) {
        final String swapTime = customSwapTime.getCommand();

        if (arbitrageService.getLeftMarketService().getMarketStaticData() == MarketStaticData.BITMEX) {
            ((BitmexService) arbitrageService.getLeftMarketService()).getBitmexSwapService().setCustomSwapTime(swapTime);
        }

        return new ResultJson(swapTime, "");
    }

    public ResultJson resetTimeCompare() {
        final String timeCompareString = bitmexTimeService.resetTimeCompare();
        return new ResultJson(timeCompareString, "");
    }

    public ResultJson updateTimeCompareUpdating(ChangeRequestJson changeRequestJson) {
        final String command = changeRequestJson.getCommand();
        final Integer timeCompareUpdating = bitmexTimeService.updateTimeCompareUpdating(Integer.valueOf(command));
        return new ResultJson(String.valueOf(timeCompareUpdating), "");
    }

}
