package com.bitplay.okex.v5.service;

import com.bitplay.externalapi.PrivateApi;
import com.bitplay.model.Leverage;
import com.bitplay.model.Pos;
import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.okex.v5.ApiConfigurationV5;
import com.bitplay.okex.v5.client.ApiClient;
import com.bitplay.okex.v5.dto.ChangeLeverRequest;
import com.bitplay.okex.v5.dto.adapter.AccountConverter;
import com.bitplay.okex.v5.dto.adapter.DtoToModelConverter;
import com.bitplay.okex.v5.dto.adapter.OkexOrderConverter;
import com.bitplay.okex.v5.dto.param.Order;
import com.bitplay.okex.v5.dto.param.OrderAmendRequest;
import com.bitplay.okex.v5.dto.param.OrderCnlRequest;
import com.bitplay.okex.v5.dto.result.Account;
import com.bitplay.okex.v5.dto.result.LeverageResult;
import com.bitplay.okex.v5.dto.result.LeverageResult.LeverageResultData;
import com.bitplay.okex.v5.dto.result.OkexOnePositionV5;
import com.bitplay.okex.v5.dto.result.OkexPosV5;
import com.bitplay.okex.v5.dto.result.OrderDetail;
import com.bitplay.okex.v5.dto.result.OrderResult;
import com.bitplay.okex.v5.dto.result.OrdersDetailResult;
import com.bitplay.okex.v5.enums.FuturesOrderTypeEnum;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.dto.trade.LimitOrder;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class PrivateApiV5 implements PrivateApi {

    private volatile ApiClient client;
    private volatile TradeApi api;
    private String instType;


    public PrivateApiV5(ApiConfigurationV5 config, String instType, String arbTypeUpperCase) {
        this.client = new ApiClient(config, arbTypeUpperCase);
        this.api = client.createService(TradeApi.class);
        this.instType = instType;
    }

    @Override
    public Pos getPos(String instrumentId) {
//        final Object jsonObject = this.client.executeSync(this.api.getInstrumentPositionTest(instType, instrumentId));
//        System.out.println(jsonObject);
//        return null;
        final OkexOnePositionV5 position = this.client.executeSync(this.api.getInstrumentPosition(instType, instrumentId));

        Pos pos = position.getOne()
                .map(OkexPosV5::toPos)
                .orElse(null);
        if (pos == null) {
            // get raw answer
            final Object jsonObject = this.client.executeSync(this.api.getInstrumentPositionTest(instType, instrumentId));
            if (position.getCode() != null && position.getCode().equals("0") && StringUtils.isBlank(position.getMsg())) {
                log.info("use empty pos for instType: " + instType + ", instrumentId: " + instrumentId
                        + ". Double check: " + jsonObject.toString());
                pos = Pos.emptyPos();
            } else {
                log.error("Position error. Double check: " + jsonObject.toString());
            }
        }
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
        final FuturesOrderTypeEnum orderTypeEnum = (FuturesOrderTypeEnum) extraFlags.get(0);
        final Order order = OkexOrderConverter.createOrder(instrumentId, orderType, thePrice, amount, orderTypeEnum, leverage);
        final OrderResult orderResult = this.client.executeSync(this.api.placeOrder(order));
        return OkexOrderConverter.convertOrderResult(orderResult);
    }

    @Override
    public OrderResultTiny cnlOrder(String instrumentId, String orderId) {
        final OrderResult orderResult = this.client.executeSync(this.api.cancelOrder(
                new OrderCnlRequest(instrumentId, orderId)
        ));
        return OkexOrderConverter.convertOrderResult(orderResult);
    }

    @Override
    public List<LimitOrder> getOpenLimitOrders(String instrumentId, CurrencyPair currencyPair) {
        final OrdersDetailResult openOrdersDetailResult = this.client.executeSync(this.api.getOrdersWithState(instType, instrumentId));
        final List<OrderDetail> orders = openOrdersDetailResult.getData();
        return DtoToModelConverter.convertOrders(orders, currencyPair);
    }

    @Override
    public LimitOrder getLimitOrder(String instrumentId, String orderId, CurrencyPair currencyPair) {
        final OrdersDetailResult openOrdersDetailResult = this.client.executeSync(this.api.getOrder(instrumentId, orderId));
        final List<OrderDetail> orders = openOrdersDetailResult.getData();
        return orders.size() > 0
                ? DtoToModelConverter.convertOrder(orders.get(0), currencyPair)
                : null;
    }

    @Override
    public OrderResultTiny moveLimitOrder(String instrumentId, String orderId, BigDecimal newPrice) {

        final OrderResult orderResult = this.client.executeSync(this.api.amendOrder(
                new OrderAmendRequest(instrumentId, orderId, newPrice)
        ));
        return OkexOrderConverter.convertOrderResult(orderResult);
    }

    @Override
    public Leverage getLeverage(String instrumentId) {
        final LeverageResult r = this.client.executeSync(this.api.getLeverRate(instrumentId, "cross"));

        if (r != null && r.getOne() != null) {
            return new Leverage(r.getOne().getLever(), "");
        }
        return null;
    }

    @Override
    public Leverage changeLeverage(String instId, String newLeverageStr) {
        final LeverageResult r = this.client.executeSync(this.api.changeLeverageOnCross(new ChangeLeverRequest(instId, newLeverageStr)));
        if (r != null && r.getOne() != null) {
            final LeverageResultData l = r.getOne();
            return new Leverage(l.getLever(), "instId=" + l.getInstId() + " , lever=" + l.getLever());
        }
        return null;
    }


    @Override
    public boolean notCreated() {
        return this.client == null || this.api == null;
    }


}
