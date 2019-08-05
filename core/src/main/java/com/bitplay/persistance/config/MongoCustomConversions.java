package com.bitplay.persistance.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import org.bson.types.Decimal128;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.CustomConversions;

class MongoCustomConversions {

    //    @WritingConverter
    private static class BigDecimalDecimal128Converter implements Converter<BigDecimal, Decimal128> {

        @Override
        public Decimal128 convert(@NonNull BigDecimal source) {
            return new Decimal128(source);
        }
    }

    //    @ReadingConverter
    private static class Decimal128BigDecimalConverter implements Converter<Decimal128, BigDecimal> {

        @Override
        public BigDecimal convert(@NonNull Decimal128 source) {
            return source.bigDecimalValue();
        }

    }

    static CustomConversions customConversions() {
        List<Converter> converters = new ArrayList<>();
        converters.add(new BigDecimalDecimal128Converter());
        converters.add(new Decimal128BigDecimalConverter());
        return new CustomConversions(converters);
    }
}
