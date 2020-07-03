package com.bitplay.external;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
class Destination {

    private List<String> channels;
    private String hostLabel;
}
