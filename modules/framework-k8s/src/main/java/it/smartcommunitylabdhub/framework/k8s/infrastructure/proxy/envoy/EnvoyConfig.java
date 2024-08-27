package it.smartcommunitylabdhub.framework.k8s.infrastructure.proxy.envoy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EnvoyConfig {
    @JsonProperty("static_resources")
    private StaticResources staticResources;

    @Data
    @Builder
    public static class StaticResources {
        private List<Listener> listeners;
        private List<Cluster> clusters;
    }

    @Data
    @Builder
    public static class Listener {
        private String name;
        private Address address;

        @JsonProperty("filter_chains")
        private List<FilterChain> filterChains;
    }

    @Data
    @Builder
    public static class Address {
        @JsonProperty("socket_address")
        private SocketAddress socketAddress;
    }

    @Data
    @Builder
    public static class SocketAddress {
        private String address;

        @JsonProperty("port_value")
        private int portValue;
    }

    @Data
    @Builder
    public static class FilterChain {
        private List<Filter> filters;
    }

    @Data
    @Builder
    public static class Filter {
        private String name;

        @JsonProperty("typed_config")
        private TypedConfig typedConfig;
    }

    @Data
    @Builder
    public static class TypedConfig {
        @JsonProperty("@type")
        private String type;

        @JsonProperty("stat_prefix")
        private String statPrefix;

        @JsonProperty("http_filters")
        private List<HttpFilter> httpFilters;

        @JsonProperty("route_config")
        private RouteConfig routeConfig;
    }

    @Data
    @Builder
    public static class HttpFilter {
        private String name;

        @JsonProperty("typed_config")
        private TypedConfig typedConfig;
    }

    @Data
    @Builder
    public static class RouteConfig {
        private String name;

        @JsonProperty("virtual_hosts")
        private List<VirtualHost> virtualHosts;
    }

    @Data
    @Builder
    public static class VirtualHost {
        private String name;
        private List<String> domains;
        private List<Route> routes;
    }

    @Data
    @Builder
    public static class Route {
        private String name;
        private Match match;
        private RouteAction route;
    }

    @Data
    @Builder
    public static class Match {
        private String prefix;
    }

    @Data
    @Builder
    public static class RouteAction {
        private String cluster;
    }

    @Data
    @Builder
    public static class Cluster {
        private String name;

        @JsonProperty("type")
        private String type;

        @JsonProperty("lb_policy")
        private String lbPolicy;

        @JsonProperty("load_assignment")
        private LoadAssignment loadAssignment;
    }

    @Data
    @Builder
    public static class LoadAssignment {
        @JsonProperty("cluster_name")
        private String clusterName;
        private List<EndpointGroup> endpoints;
    }

    @Data
    @Builder
    public static class EndpointGroup {
        @JsonProperty("lb_endpoints")
        private List<LbEndpoint> lbEndpoints;
    }

    @Data
    @Builder
    public static class LbEndpoint {
        private Endpoint endpoint;
    }

    @Data
    @Builder
    public static class Endpoint {
        private Address address;
    }
}
