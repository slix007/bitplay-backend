package com.bitplay.xchange.utils.nonce;

import si.mazi.rescu.SynchronizedValueFactory;

public class CurrentTimeSecondsPlus30NonceFactory implements SynchronizedValueFactory<Long> {

  @Override
  public Long createValue() {

    return System.currentTimeMillis() / 1000L + 30;
  }
}
