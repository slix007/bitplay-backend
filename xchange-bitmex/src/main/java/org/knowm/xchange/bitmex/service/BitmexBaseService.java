package org.knowm.xchange.bitmex.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitmex.BitmexAuthenticated;
import org.knowm.xchange.bitmex.BitmexPublic;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexBaseService extends BaseExchangeService implements BaseService {

    protected final BitmexPublic bitmexPublic;
    protected final BitmexAuthenticated bitmexAuthenticated;

    /**
     * Constructor
     */
    protected BitmexBaseService(Exchange exchange) {
        super(exchange);
        bitmexPublic = new BitmexPublic();
        bitmexAuthenticated = new BitmexAuthenticated();
    }
}
