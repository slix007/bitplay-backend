package com.bitplay.market.okcoin.convert;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface SuperConverter<A, B> extends Function<A, B> {

    default List<B> convertToList(final List<A> input) {
        return input.stream().map(this::apply).collect(Collectors.toList());
    }
}