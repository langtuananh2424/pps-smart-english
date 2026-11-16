package com.pps.smartenglish.ai.speaking;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Quy đổi một số đo sang band bằng bảng ngưỡng cấu hình ("ngưỡng=band,..."). */
final class BandTable {

    private record Row(double threshold, double band) {}

    private final List<Row> rows;
    private final boolean higherIsBetter;

    private BandTable(String spec, boolean higherIsBetter) {
        this.higherIsBetter = higherIsBetter;
        Comparator<Row> order = Comparator.comparingDouble(Row::threshold);
        this.rows = Arrays.stream(spec.split(",")).map(String::trim).filter(p -> !p.isEmpty()).map(p -> {
            String[] kv = p.split("=");
            return new Row(Double.parseDouble(kv[0].trim()), Double.parseDouble(kv[1].trim()));
        }).sorted(higherIsBetter ? order.reversed() : order).toList();
    }

    static BandTable atLeast(String spec) {
        return new BandTable(spec, true);
    }

    static BandTable atMost(String spec) {
        return new BandTable(spec, false);
    }

    double band(double value) {
        for (Row r : rows) {
            if (higherIsBetter ? value >= r.threshold : value <= r.threshold) {
                return r.band;
            }
        }
        return 1;
    }
}
