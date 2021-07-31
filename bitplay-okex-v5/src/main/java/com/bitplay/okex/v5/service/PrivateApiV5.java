package com.bitplay.okex.v5.service;

import com.bitplay.externalapi.PrivateApi;
import com.bitplay.model.Leverage;
import com.bitplay.model.Pos;
import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.okex.v5.ApiConfigurationV5;
import com.bitplay.okex.v5.client.ApiClient;
import com.bitplay.okex.v5.dto.adapter.AccountConverter;
import com.bitplay.okex.v5.dto.result.Account;
import com.bitplay.okex.v5.dto.result.OkexOnePositionV5;
import com.bitplay.okex.v5.dto.result.OkexPosV5;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.exceptions.NotYetImplementedForExchangeException;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrivateApiV5 implements PrivateApi {

    private volatile ApiClient client;
    private volatile TradeApi api;
    private String instType;


    public PrivateApiV5(ApiConfigurationV5 config, String instType) {
        this.client = new ApiClient(config);
        this.api = client.createService(TradeApi.class);
        this.instType = instType;
    }

    @Override
    public Pos getPos(String instrumentId) {
        final OkexOnePositionV5 position = getInstrumentPositionApi(instrumentId);
        return position.getOne().isPresent()
                ? OkexPosV5.toPos(position.getOne().get())
                : Pos.emptyPos();
    }

    public OkexOnePositionV5 getInstrumentPositionApi(String instrumentId) {
        final OkexOnePositionV5 pos = this.client.executeSync(this.api.getInstrumentPosition(instType, instrumentId));
//        System.out.println("POS_VAL");
//        System.out.println(pos);
        return pos;
    }


    @Override
    public AccountInfoContracts getAccount(String currencyCode) {
        final Account byCurrencyApi = this.client.executeSync(this.api.getBalance(currencyCode));
//        System.out.println(byCurrencyApi);
        return AccountConverter.convert(byCurrencyApi);
//        throw new NotYetImplementedForExchangeException();

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
        return this.client == null || this.api == null;
    }


}
