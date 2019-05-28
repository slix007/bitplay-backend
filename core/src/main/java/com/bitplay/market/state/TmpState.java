package com.bitplay.market.state;

import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.LiqInfo;
import io.swagger.client.model.OrderBook;
import lombok.Data;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.trade.OpenOrders;

@Data
public class TmpState {

    private OrderBook orderBook;
    private AccountInfoContracts accountInfoContracts = new AccountInfoContracts();
    private Position position;

    private Affordable affordable;
    private LiqInfo liqInfo;

    private OpenOrders openOrders;

}
