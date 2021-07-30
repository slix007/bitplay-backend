package com.bitplay.xchange.okcoin.service;

import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.service.BaseExchangeService;
import com.bitplay.xchange.service.BaseService;

public class OkCoinBaseService extends BaseExchangeService implements BaseService {

  /** Set to true if international site should be used */
  protected final boolean useIntl;

  /**
   * Constructor
   *
   * @param exchange
   */
  public OkCoinBaseService(Exchange exchange) {

    super(exchange);

    useIntl = (Boolean) exchange.getExchangeSpecification().getExchangeSpecificParameters().get("Use_Intl");

  }

  protected String createDelimitedString(String[] items) {

    StringBuilder commaDelimitedString = null;
    if (items != null) {
      for (String item : items) {
        if (commaDelimitedString == null) {
          commaDelimitedString = new StringBuilder(item);
        } else {
          commaDelimitedString.append(",").append(item);
        }
      }
    }

    return (commaDelimitedString == null) ? null : commaDelimitedString.toString();
  }
}
