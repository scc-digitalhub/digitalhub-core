package it.smartcommunitylabdhub.runtime.hpcdl.framework.config;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.monitor.HPCDLMonitor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;

@Configuration
@Order(6)
public class HCPDLMonitorConfig implements InitializingBean {

    @Autowired
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;

    @Autowired
    private HPCDLMonitor monitor;

    @Value("${monitors.delay}")
    private int delay;

    @Value("${monitors.min-delay}")
    private int minDelay;

    public void afterPropertiesSet() {
        Assert.isTrue(delay >= minDelay, "Delay must be greater than 0");

        // staggered start up for monitors
        Instant start = Instant.now();

        Class<?> builderClass = monitor.getClass();
        if (builderClass.isAnnotationPresent(MonitorComponent.class) && monitor instanceof Runnable) {
            MonitorComponent annotation = builderClass.getAnnotation(MonitorComponent.class);
            //TODO wrap with nested class and split/group frameworks
            String framework = annotation.framework();
            start = start.plus(10, ChronoUnit.SECONDS);
            threadPoolTaskScheduler.scheduleWithFixedDelay(monitor, start, Duration.ofSeconds(delay));
        }
    }
}
