package it.smartcommunitylabdhub.metrics.prometheus.client;

import com.fasterxml.jackson.annotation.JsonFormat;

// Prometheus vector values are arrays: [unix_epoch_float, value_string]
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public record MetricPoint(Double timestamp, String value) {}
