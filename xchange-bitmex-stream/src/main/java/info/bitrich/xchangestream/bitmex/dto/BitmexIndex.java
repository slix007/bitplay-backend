package info.bitrich.xchangestream.bitmex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 6/26/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitmexIndex {

    private final BigDecimal markPrice;
    private final Date timestamp;

    public BitmexIndex(@JsonProperty("markPrice") BigDecimal markPrice,
                       @JsonProperty("timestamp") Date timestamp) {
        this.markPrice = markPrice;
        this.timestamp = timestamp;
    }

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public Date getTimestamp() {
        return timestamp;
    }
}
