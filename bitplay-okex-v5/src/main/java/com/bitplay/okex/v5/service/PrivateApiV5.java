package com.bitplay.okex.v5.service;

import com.bitplay.externalapi.PrivateApi;
import com.bitplay.model.Leverage;
import com.bitplay.model.Pos;
import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.okex.v5.ApiConfiguration;
import com.bitplay.okex.v5.client.ApiClient;
import com.bitplay.okex.v5.dto.result.OkexAllPositions;
import com.bitplay.okex.v5.dto.result.OkexOnePosition;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.exceptions.NotYetImplementedForExchangeException;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrivateApiV5 implements PrivateApi {

    private volatile ApiClient client;
//    private volatile SwapTradeApi api;


    public PrivateApiV5(ApiConfiguration config) {
//        super(config);
    }

    @Override
    public Pos getPos(String instrumentId) {
        final OkexOnePosition position = getInstrumentPositionApi(instrumentId);
        return position.getOne().isPresent()
                ? OkexAllPositions.toPos(position.getOne().get())
                : Pos.emptyPos();
    }

    public OkexOnePosition getInstrumentPositionApi(String instrumentId) {
//        return this.client.executeSync(this.api.getInstrumentPosition(instrumentId));
        throw new NotYetImplementedForExchangeException();
    }


    @Override
    public AccountInfoContracts getAccount(String currencyCode) {
//        final Account byCurrencyApi = getAccountsByCurrencyApi(currencyCode);
//        return AccountConverter.convert(byCurrencyApi);
        throw new NotYetImplementedForExchangeException();

    }

    @Override
    public OrderResultTiny limitOrder(String instrumentId, OrderType orderType, BigDecimal thePrice, BigDecimal amount, BigDecimal leverage,
            List<Object> extraFlags) {
//        final FuturesOrderTypeEnum orderTypeEnum = (FuturesOrderTypeEnum) extraFlags.get(0);
//        final Order order =
//                OkexOrderConverter.createOrder(instrumentId, orderType, thePrice, amount, orderTypeEnum, leverage);
//        final OrderResult orderResult = orderApi(order);
//        return OkexOrderConverter.convertOrderResult(orderResult);
        throw new NotYetImplementedForExchangeException();

    }

    @Override
    public OrderResultTiny cnlOrder(String instrumentId, String orderId) {
//        final OrderResult orderResult = cancelOrder(instrumentId, orderId);
//        return OkexOrderConverter.convertOrderResult(orderResult);
        throw new NotYetImplementedForExchangeException();

    }

    @Override
    public List<LimitOrder> getOpenLimitOrders(String instrumentId, CurrencyPair currencyPair) {
//        final List<OrderDetail> orders = getOpenOrders(instrumentId);
//        return DtoToModelConverter.convertOrders(orders, currencyPair);
        throw new NotYetImplementedForExchangeException();

    }

    @Override
    public LimitOrder getLimitOrder(String instrumentId, String orderId, CurrencyPair currencyPair) {
//        final OrderDetail order = getOrder(instrumentId, orderId);
//        return DtoToModelConverter.convertOrder(order, currencyPair);
        throw new NotYetImplementedForExchangeException();

    }

    @Override
    public Leverage getLeverage(String currencyPair) {
//        final LeverageResult r = getInstrumentLeverRate(currencyPair);
//        if (!r.getMargin_mode().equals("crossed")) {
//            log.warn("LeverageResult WARNING: margin_mode is " + r.getMargin_mode());
//        } else {
//            return new Leverage(new BigDecimal(r.getLeverage()), r.getResult());
//        }
//        return null;
        throw new NotYetImplementedForExchangeException();

    }

    @Override
    public Leverage changeLeverage(String newCurrOrInstrId, String newLeverageStr) {
//        final LeverageResult r = changeLeverageOnCross(newCurrOrInstrId, newLeverageStr);
//        return new Leverage(new BigDecimal(r.getLeverage()), r.getResult());
        throw new NotYetImplementedForExchangeException();

    }


    @Override
    public boolean notCreated() {
        return false;
    }


}
