package com.bitplay.xchange.okcoin.service;

import java.io.IOException;
import java.math.BigDecimal;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.currency.Currency;
import com.bitplay.xchange.dto.account.AccountInfo;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.exceptions.NotAvailableFromExchangeException;
import com.bitplay.xchange.okcoin.OkCoinAdapters;
import com.bitplay.xchange.service.account.AccountService;

public class OkCoinFuturesAccountService extends OkCoinAccountServiceRaw implements AccountService {

  /**
   * Constructor
   *
   * @param exchange
   */
  public OkCoinFuturesAccountService(Exchange exchange) {

    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {

    return OkCoinAdapters.adaptAccountInfoFutures(getFutureUserInfo());
  }

  public AccountInfoContracts getAccountInfoContracts(Currency currency) throws IOException {

    return OkCoinAdapters.adaptAccountInfoContractsFutures(currency, getFutureUserInfo());
  }

  @Override
  public String withdrawFunds(Currency currency, BigDecimal amount, String address) throws IOException {

    throw new NotAvailableFromExchangeException();
  }

  @Override
  public String requestDepositAddress(Currency currency, String... args) throws IOException {

    throw new NotAvailableFromExchangeException();
  }

}
