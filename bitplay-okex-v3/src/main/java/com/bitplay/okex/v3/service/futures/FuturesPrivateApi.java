package com.bitplay.okex.v3.service.futures;

import com.bitplay.model.Leverage;
import com.bitplay.model.Pos;
import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.dto.futures.result.Account;
import com.bitplay.okex.v3.dto.futures.result.LeverageResult;
import com.bitplay.okex.v3.dto.futures.result.OkexAllPositions;
import com.bitplay.okex.v3.dto.futures.result.OkexOnePosition;
import com.bitplay.okex.v3.dto.futures.result.OrderDetail;
import com.bitplay.okex.v3.dto.futures.result.OrderResult;
import com.bitplay.okex.v3.enums.FuturesOrderTypeEnum;
import com.bitplay.okex.v3.service.futures.adapter.AccountConverter;
import com.bitplay.okex.v3.service.futures.adapter.DtoToModelConverter;
import com.bitplay.okex.v3.service.futures.adapter.OkexOrderConverter;
import com.bitplay.okex.v3.service.futures.api.FuturesTradeApiServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
public class FuturesPrivateApi extends FuturesTradeApiServiceImpl {

    public FuturesPrivateApi(ApiConfiguration config) {
        super(config);
    }

    @Override
    public Pos getPos(String instrumentId) {
        final OkexOnePosition position = getInstrumentPositionApi(instrumentId);
        return position.getOne().isPresent()
                ? OkexAllPositions.toPos(position.getOne().get())
                : Pos.emptyPos();
    }


    @Override
    public AccountInfoContracts getAccount(String currencyCode) {
        final Account byCurrencyApi = getAccountsByCurrencyApi(currencyCode);
        return AccountConverter.convert(byCurrencyApi);
    }

    @Override
    public OrderResultTiny limitOrder(String instrumentId, Order.OrderType orderType, BigDecimal thePrice, BigDecimal amount, BigDecimal leverage,
                                      List<Object> extraFlags) {
        final FuturesOrderTypeEnum orderTypeEnum = (FuturesOrderTypeEnum) extraFlags.get(0);
        final com.bitplay.okex.v3.dto.futures.param.Order order =
                OkexOrderConverter.createOrder(instrumentId, orderType, thePrice, amount, orderTypeEnum, leverage);
        final OrderResult orderResult = orderApi(order);
        return OkexOrderConverter.convertOrderResult(orderResult);
    }

    @Override
    public OrderResultTiny cnlOrder(String instrumentId, String orderId) {
        final OrderResult orderResult = cancelOrder(instrumentId, orderId);
        return OkexOrderConverter.convertOrderResult(orderResult);
    }

    @Override
    public List<LimitOrder> getOpenLimitOrders(String instrumentId, CurrencyPair currencyPair) {
        final List<OrderDetail> orders = getOpenOrders(instrumentId);
        return DtoToModelConverter.convertOrders(orders, currencyPair);
    }

    @Override
    public LimitOrder getLimitOrder(String instrumentId, String orderId, CurrencyPair currencyPair) {
        final OrderDetail order = getOrder(instrumentId, orderId);
        return DtoToModelConverter.convertOrder(order, currencyPair);
    }

    @Override
    public Leverage getLeverage(String instrumentId) {
        final LeverageResult r = getInstrumentLeverRate(instrumentId);
        if (!r.getMargin_mode().equals("crossed")) {
            log.warn("LeverageResult WARNING: margin_mode is " + r.getMargin_mode());
        } else {
            if (!r.getInstrument_id().toUpperCase().equals(instrumentId)) {
                log.warn("LeverageResult WARNING: currency is different " + r.getCurrency());
            } else {
                return new Leverage(new BigDecimal(r.getLeverage()), r.getResult());
            }
        }
        return null;
    }

    @Override
    public Leverage changeLeverage(String newCurrOrInstrId, String newLeverageStr) {
        final LeverageResult r = changeLeverageOnCross(newCurrOrInstrId, newLeverageStr);
        return new Leverage(new BigDecimal(r.getLeverage()), r.getResult());
    }

}
