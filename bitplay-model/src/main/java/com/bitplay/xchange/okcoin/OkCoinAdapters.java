package com.bitplay.xchange.okcoin;

import com.bitplay.xchange.dto.Order.OrderStatus;
import com.bitplay.xchange.dto.Order.OrderType;

public final class OkCoinAdapters {

    public static OrderType adaptOrderType(String type) {

        switch (type) {
            case "buy":
            case "buy_market":
                return OrderType.BID;
            case "sell":
            case "sell_market":
                return OrderType.ASK;
            default:
                return null;
        }

    }

    public static OrderType convertType(String orderType) {
        if (orderType == null) {
            return null;
        }
        // Type (1: open long 2: open short 3: close long 4: close short)
        switch (orderType) {
            case "buy"://OPEN_LONG:
                return OrderType.BID;
            case "sell"://OPEN_SHORT:
                return OrderType.ASK;
//            case "3"://CLOSE_LONG:
//                return OrderType.EXIT_BID;
//            case "4"://CLOSE_SHORT:
//                return OrderType.EXIT_ASK;
            default:
                throw new IllegalArgumentException("enum is wrong");
        }
    }


    public static OrderStatus convertStatus(String state) {
        // for OPEN ORDERS:
        //live
        //partially_filled

        // for DETAILS:
        //State
        //canceled
        //live
        //partially_filled
        //filled
        switch (state) {
            case "canceled":
                return OrderStatus.CANCELED;
            case "live":
                return OrderStatus.PENDING_NEW;
            case "effective":
                return OrderStatus.NEW;
            case "partially_filled":
                return OrderStatus.PARTIALLY_FILLED;
            case "filled":
                return OrderStatus.FILLED;
            case "order_failed":
                return OrderStatus.REJECTED;
        }
        throw new IllegalArgumentException("wrong orderStatus " + state);
    }

}
