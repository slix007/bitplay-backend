package com.bitplay.xchange.service.trade.params;

import com.bitplay.xchange.service.trade.TradeService;
import java.util.Date;
import com.bitplay.xchange.currency.CurrencyPair;

/**
 * Generic {@link TradeHistoryParams} implementation that implements all the interfaces in the hierarchy and can be safely (without getting
 * exceptions, if that all the required fields are non-null) passed to any implementation of
 * {@link TradeService#getTradeHistory(TradeHistoryParams)} .
 */
public class TradeHistoryParamsAll
    implements TradeHistoryParamsTimeSpan, TradeHistoryParamPaging, TradeHistoryParamsIdSpan, TradeHistoryParamOffset, TradeHistoryParamCurrencyPair {

  private Integer pageLength;
  private Integer pageNumber;
  private String startId;
  private String endId;
  private Date startTime;
  private Date endTime;
  private Long offset;
  private CurrencyPair pair;

  @Override
  public void setPageLength(Integer count) {

    this.pageLength = count;
  }

  @Override
  public Integer getPageLength() {

    return pageLength;
  }

  @Override
  public String getStartId() {

    return startId;
  }

  @Override
  public void setEndId(String endId) {

    this.endId = endId;
  }

  @Override
  public String getEndId() {

    return endId;
  }

  @Override
  public void setStartId(String from) {

    startId = from;
  }

  @Override
  public void setEndTime(Date to) {

    endTime = to;
  }

  @Override
  public Date getEndTime() {

    return endTime;
  }

  @Override
  public void setStartTime(Date startTime) {

    this.startTime = startTime;
  }

  @Override
  public Date getStartTime() {

    return startTime;
  }

  @Override
  public void setOffset(Long offset) {

    this.offset = offset;
  }

  @Override
  public Long getOffset() {

    if (offset != null || pageLength == null || pageNumber == null)
      return offset;
    else
      return (long) pageLength * pageNumber;
  }

  @Override
  public Integer getPageNumber() {

    return pageNumber;
  }

  @Override
  public void setPageNumber(Integer pageNumber) {

    this.pageNumber = pageNumber;
  }

  @Override
  public CurrencyPair getCurrencyPair() {

    return pair;
  }

  @Override
  public void setCurrencyPair(CurrencyPair pair) {

    this.pair = pair;
  }
}
