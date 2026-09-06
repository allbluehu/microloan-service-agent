package com.example.microloan.ticket;

import io.micrometer.core.instrument.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.web.bind.annotation.*;

/** 仅向坐席返回白名单聚合指标，不开放通用 Actuator 指标读取。 */
@RestController
@RequestMapping("/api/v1/support/metrics")
public class SupportMetricsController {
    private final MeterRegistry registry;
    public SupportMetricsController(MeterRegistry registry) { this.registry = registry; }

    @GetMapping
    public List<Metric> metrics() {
        return registry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("microloan.agent.")
                        || m.getId().getName().equals("gen_ai.client.token.usage"))
                .map(m -> new Metric(m.getId().getName(), m.getId().getTags().stream()
                        .map(t -> t.getKey() + "=" + t.getValue()).toList(),
                        m.measure().iterator().hasNext() ? measurements(m) : List.of()))
                .toList();
    }

    private List<MeasurementView> measurements(Meter meter) {
        var result = new java.util.ArrayList<MeasurementView>();
        meter.measure().forEach(m -> result.add(new MeasurementView(m.getStatistic().name(), m.getValue())));
        if (meter instanceof Timer timer) {
            for (var percentile : timer.takeSnapshot().percentileValues()) {
                result.add(new MeasurementView("P" + Math.round(percentile.percentile() * 100) + "_MS",
                        percentile.value(TimeUnit.MILLISECONDS)));
            }
        }
        return result;
    }

    public record Metric(
            /** 白名单指标名称。 */ String name,
            /** 固定枚举或模型配置产生的低基数标签。 */ List<String> tags,
            /** 累计值和当前实例分位数。 */ List<MeasurementView> measurements
    ) {}
    public record MeasurementView(
            /** 测量名称，时间总量单位为秒，P95 为毫秒。 */ String statistic,
            /** 当前实例累计测量值。 */ double value
    ) {}
}
