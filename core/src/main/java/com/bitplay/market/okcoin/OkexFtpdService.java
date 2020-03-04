package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.dto.ThrottledWarn;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.persistance.domain.settings.OkexFtpd;
import com.bitplay.persistance.domain.settings.OkexFtpdType;
import info.bitrich.xchangestream.okexv3.dto.marketdata.OkcoinPriceRange;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
@Slf4j
@Getter
public class OkexFtpdService {

    private ThrottledWarn throttledLog = new ThrottledWarn(log, 30);
    private final OkCoinService okCoinService;

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

        final BigDecimal ftpd;
        if (bodMax.compareTo(bod) < 0 || bodMin.compareTo(bod) < 0) {
            log.warn(String.format("ftpd=0 because bod_max=(%s)<bod(%s) or bod_min(%s)<bod(%s)", bodMax, bod, bodMin, bod));
            ftpd = BigDecimal.ZERO;
        } else if (okexFtpd.getOkexFtpdType() == OkexFtpdType.PTS) {
            ftpd = okexFtpd.getOkexFtpd();
        } else {
            //else PERCENT
            //FTPD_buy = bod_max * percent / 100;
            //FTPD_sell = bod_min * percent / 100;
            BigDecimal bodOne = orderType == Order.OrderType.ASK || orderType == Order.OrderType.EXIT_BID
                    ? bodMin
                    : bodMax; // orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK
            ftpd = bodOne.multiply(okexFtpd.getOkexFtpd()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        }
        return createPriceByFtpd(orderType, okcoinPriceRange, ftpd);

    }

    public static BigDecimal createPriceByFtpd(Order.OrderType orderType, OkcoinPriceRange okcoinPriceRange, BigDecimal okexFtpdPts) {
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
        if (okexFtpd.getOkexFtpdType() == OkexFtpdType.PTS) {
            return "FTPD(usd)=" + okexFtpd.getOkexFtpd();
        }
        return String.format("FTPD(percent)=%s, bod=%s, bod_max=%s, bod_min=%s", okexFtpd.getOkexFtpd(), okexFtpd.getOkexFtpdBod(),
                bodMax, bodMin);
    }

}
