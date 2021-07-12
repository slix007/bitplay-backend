package com.bitplay.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InfluxdbMetricsWriter {

//    @Bean
//    @ExportMetricWriter
//    GaugeWriter influxMetricsWriter() {
//        InfluxDB influxDB = InfluxDBFactory.connect("http://localhost:8086", "root", "root");
//        String dbName = "fplay-metrics";
//        influxDB.setDatabase(dbName);
//        influxDB.setRetentionPolicy("one_day");
//        influxDB.enableBatch(10, 1000, TimeUnit.MICROSECONDS);
//
//        return value -> {
//            Point point = Point
//                    .measurement(value.getName())
//                    .time(value.getTimestamp().getTime(), TimeUnit.MILLISECONDS)
//                    .addField("value", value.getValue())
//                    .build();
//            influxDB.write(point);
//            log.info("write(" + value.getName() + "): " + value.getValue());
//        };
//    }
//
//    @Bean
//    public MetricsEndpointMetricReader metricsEndpointMetricReader(final MetricsEndpoint metricsEndpoint) {
//        return new MetricsEndpointMetricReader(metricsEndpoint);
//    }
}
