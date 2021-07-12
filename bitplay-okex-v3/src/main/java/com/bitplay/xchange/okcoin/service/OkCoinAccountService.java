package com.bitplay.xchange.okcoin.service;

import java.io.IOException;
import java.math.BigDecimal;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.currency.Currency;
import com.bitplay.xchange.dto.account.AccountInfo;
import com.bitplay.xchange.exceptions.NotAvailableFromExchangeException;
import com.bitplay.xchange.okcoin.OkCoinAdapters;
import com.bitplay.xchange.okcoin.dto.account.OKCoinWithdraw;
import com.bitplay.xchange.service.account.AccountService;

public class OkCoinAccountService extends OkCoinAccountServiceRaw implements AccountService {

  /**
   * Constructor
   *
   * @param exchange
   */
  public OkCoinAccountService(Exchange exchange) {

    super(exchange);

  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {

    return OkCoinAdapters.adaptAccountInfo(getUserInfo());
  }

  @Override
  public String withdrawFunds(Currency currency, BigDecimal amount, String address) throws IOException {
    String okcoinCurrency = currency == Currency.BTC ? "btc_usd" : "btc_ltc";

    OKCoinWithdraw result = withdraw(null, okcoinCurrency, address, amount);

    if (result != null)
      return result.getWithdrawId();

    return "";
  }

  @Override
  public String requestDepositAddress(Currency currency, String... args) throws IOException {

    throw new NotAvailableFromExchangeException();
  }

}
