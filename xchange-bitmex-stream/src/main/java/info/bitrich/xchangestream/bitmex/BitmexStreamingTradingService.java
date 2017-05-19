package info.bitrich.xchangestream.bitmex;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import info.bitrich.xchangestream.bitmex.wsjsr356.StreamingServiceBitmex;
import info.bitrich.xchangestream.core.StreamingTradingService;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;

import io.reactivex.Observable;
import io.swagger.client.model.Order;

/**
 * Created by Sergey Shurmin on 5/17/17.
 */
public class BitmexStreamingTradingService implements StreamingTradingService {

    private final StreamingServiceBitmex service;

    public BitmexStreamingTradingService(StreamingServiceBitmex service) {
        this.service = service;
    }

    @Override
    public Observable<OpenOrders> getOpenOrdersObservable() {
        return service.subscribeChannel("order", "order")
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    mapper.registerModule(new JavaTimeModule());

//                    Wallet bitmexWallet = mapper.treeToValue(s.get("data").get(0), Wallet.class);
                    final ArrayList<LimitOrder> openOrders = new ArrayList<>();

                    final JsonNode jsonNode = s.get("data");
                    if (jsonNode.getNodeType().equals(JsonNodeType.ARRAY)) {
                        for (JsonNode node : jsonNode) {
                            Order order = mapper.treeToValue(node, Order.class);
                            final org.knowm.xchange.dto.Order.OrderType ordType =
                                    org.knowm.xchange.dto.Order.OrderType.valueOf(order.getOrdType());
                            final Date timestamp = Date.from(order.getTimestamp().toInstant());

                            openOrders.add(
                                    new LimitOrder(ordType,
                                            order.getAccount(),
                                            new CurrencyPair(new Currency(order.getCurrency()), new Currency(order.getSettlCurrency())),
                                            order.getOrderID(),
                                            timestamp,
                                            new BigDecimal(order.getPrice()))
                            );
                        }
                    }

                    return new OpenOrders(openOrders);
                });



    }
}
