package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.dto.ThrottledWarn;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.persistance.domain.settings.OkexFtpd;
import info.bitrich.xchangestream.okexv3.dto.marketdata.OkcoinPriceRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class OkexFtpdService {

    private ThrottledWarn throttledLog = new ThrottledWarn(log, 30);

    private volatile BigDecimal bodMax;
    private volatile BigDecimal bodMin;

    void updateFtpdPercent(OkcoinPriceRange okcoinPriceRange, BigDecimal ask1, BigDecimal bid1) {
        // где bod_max (best offer distance, дистанция от max_price до ask[1]) = max_price - ask[1];
        //bod_min (дистанция от min_price до bid[1]) = bid[1] - min_price;
        //percent  = значение с UI.
//        final OkexFtpd okexFtpd = settingsRepositoryService.getSettings().getOkexFtpd();
//        if (okexFtpd.getOkexFtpdType() == OkexFtpd.OkexFtpdType.PERCENT) {
        final BigDecimal minPrice = okcoinPriceRange.getLowest();
        final BigDecimal maxPrice = okcoinPriceRange.getHighest();
        bodMax = maxPrice.subtract(ask1);
        bodMin = bid1.subtract(minPrice);
//        }
    }

    public BigDecimal createPriceForTaker(Order.OrderType orderType, OkcoinPriceRange okcoinPriceRange, OkexFtpd okexFtpd) {
        if (bodMax == null || bodMin == null) {
            return BigDecimal.ZERO;
        }
        //Если bod_max < bod или bod_min < bod, то FTPD == 0, вне зависимости от выбранного типа pts или percent.
        final BigDecimal bod = okexFtpd.getOkexFtpdBod();
        if (bodMax.compareTo(bod) < 0 || bodMin.compareTo(bod) < 0) {
            throttledLog.warn(getFtpdDetails(okexFtpd));
            return BigDecimal.ZERO;
        }

        if (okexFtpd.getOkexFtpdType() == OkexFtpd.OkexFtpdType.PTS) {
            return createPtsFtpd(orderType, okcoinPriceRange, okexFtpd.getOkexFtpd());
        }

        //else PERCENT
        //FTPD_buy = bod_max * percent / 100;
        //FTPD_sell = bod_min * percent / 100;
        BigDecimal bodOne = orderType == Order.OrderType.ASK || orderType == Order.OrderType.EXIT_BID
                ? bodMin
                : bodMax; // orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK
        final BigDecimal divide = bodOne.multiply(okexFtpd.getOkexFtpd()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return divide;

    }

    public static BigDecimal createPtsFtpd(Order.OrderType orderType, OkcoinPriceRange okcoinPriceRange, BigDecimal okexFtpdPts) {
        //Fake taker price при buy-ордере (open long или close short):
        //FTP = max_price - FTPD, // FTPD = fake taker price dev, usd
        //Fake taker price при sell-ордере (open short или close long):
        //FTP = min_price + FTPD.
        if (okcoinPriceRange == null) {
            throw new NotYetInitializedException();
        }
        final BigDecimal minPrice = okcoinPriceRange.getLowest();
        final BigDecimal maxPrice = okcoinPriceRange.getHighest();

        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.ASK || orderType == Order.OrderType.EXIT_BID) {
            thePrice = minPrice.add(okexFtpdPts);
        } else if (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK) {
            thePrice = maxPrice.subtract(okexFtpdPts);
        }
        return thePrice;
    }


    public String getFtpdDetails(OkexFtpd okexFtpd) {
        if (okexFtpd.getOkexFtpdType() == OkexFtpd.OkexFtpdType.PTS) {
            return "FTPD(usd)=" + okexFtpd.getOkexFtpd();
        }
        return String.format("FTPD(percent)=%s, bod=%s, bod_max=%s, bod_min=%s", okexFtpd.getOkexFtpd(), okexFtpd.getOkexFtpdBod(),
                bodMax, bodMin);
    }

    public String getFtpdBodDetails(OkexFtpd okexFtpd) {
        if (okexFtpd.getOkexFtpdType() == OkexFtpd.OkexFtpdType.PTS) {
            return "";
        }
        return String.format("bod_max=%s, bod_min=%s", bodMax, bodMin);
    }
}
