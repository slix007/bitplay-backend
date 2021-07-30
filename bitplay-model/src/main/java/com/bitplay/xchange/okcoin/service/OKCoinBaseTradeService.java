package com.bitplay.xchange.okcoin.service;

import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.exceptions.ExchangeException;
import com.bitplay.xchange.okcoin.OkCoin;
import com.bitplay.xchange.okcoin.OkCoinDigest;
import com.bitplay.xchange.okcoin.OkCoinUtils;
import com.bitplay.xchange.okcoin.dto.trade.OkCoinErrorResult;
import si.mazi.rescu.RestProxyFactory;

public class OKCoinBaseTradeService extends OkCoinBaseService {

  protected final OkCoin okCoin;
  protected final OkCoinDigest signatureCreator;
  protected final String apikey;
  protected final String secretKey;

  /**
   * Constructor
   *
   * @param exchange
   */
  protected OKCoinBaseTradeService(Exchange exchange) {

    super(exchange);

    okCoin = RestProxyFactory.createProxy(OkCoin.class, exchange.getExchangeSpecification().getSslUri());
    apikey = exchange.getExchangeSpecification().getApiKey();
    secretKey = exchange.getExchangeSpecification().getSecretKey();

    signatureCreator = new OkCoinDigest(apikey, secretKey);
  }

  protected static <T extends OkCoinErrorResult> T returnOrThrow(T t) {

    if (t.isResult()) {
      return t;
    } else {
        throw new ExchangeException(String.format("Code: %s, translation: %s",
                t.getErrorCode(),
                OkCoinUtils.getErrorMessage(t.getErrorCode())));
    }
  }

}
