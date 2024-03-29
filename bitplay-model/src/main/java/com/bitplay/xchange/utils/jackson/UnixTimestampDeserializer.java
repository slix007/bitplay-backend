package com.bitplay.xchange.utils.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.Date;

/**
 * @author Matija Mazi
 */
public class UnixTimestampDeserializer extends JsonDeserializer<Date> {

  @Override
  public Date deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    return new Date(jp.getValueAsLong() * 1000L);
  }
}
