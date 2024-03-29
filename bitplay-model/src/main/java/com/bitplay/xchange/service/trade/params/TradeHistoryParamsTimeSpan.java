package com.bitplay.xchange.service.trade.params;

import com.bitplay.xchange.service.trade.TradeService;
import java.util.Date;

/**
 * Parameters type for {@link TradeService#getTradeHistory(TradeHistoryParams)} with start and end
 * timestamps.
 */
public interface TradeHistoryParamsTimeSpan extends TradeHistoryParams {

  void setStartTime(Date startTime);

  Date getStartTime();

  void setEndTime(Date endTime);

  Date getEndTime();

}
