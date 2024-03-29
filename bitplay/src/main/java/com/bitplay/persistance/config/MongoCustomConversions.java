package com.bitplay.persistance.config;

import com.bitplay.persistance.domain.settings.ContractMode;
import com.bitplay.persistance.domain.settings.ContractType;
import lombok.NonNull;
import org.bson.types.Decimal128;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.CustomConversions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    private static class ContractTypeConverter implements Converter<String, ContractType> {
        @Override
        public ContractType convert(String source) {
            return ContractMode.parseContractType(source);
        }
    }

    static CustomConversions customConversions() {
        List<Converter> converters = new ArrayList<>();
        converters.add(new BigDecimalDecimal128Converter());
        converters.add(new Decimal128BigDecimalConverter());
        converters.add(new ContractTypeConverter());
        return new CustomConversions(converters);
    }
}
