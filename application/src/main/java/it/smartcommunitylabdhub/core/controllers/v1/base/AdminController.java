package it.smartcommunitylabdhub.core.controllers.v1.base;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.swagger.v3.oas.annotations.Operation;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.components.logs.InMemoryLogAppender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
public class AdminController {

    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final Map<String, Level> loggerLevels = new HashMap<>();

    @Autowired
    private InMemoryLogAppender inMemoryLogAppender;

    @Operation(summary = "List logs", description = "Return a list of recent logs")
    @GetMapping(path = "/logs", produces = "application/json; charset=UTF-8")
    public List<InMemoryLogAppender.LogEntry> getLogs() {
        return inMemoryLogAppender.recent();
    }

    @GetMapping("/logs/level")
    public Map<String, String> getLogLevel(@RequestParam(required = false) String logger) {
        if (StringUtils.hasText(logger)) {
            if (!logger.startsWith("it.smartcommunitylab")) {
                throw new IllegalArgumentException("logger name must start with 'it.smartcommunitylab'");
            }

            Level level = loggerLevels.get(logger);
            return Map.of("logger", logger, "level", level != null ? level.toString() : Level.ERROR.toString());
        }

        return loggerLevels
            .entrySet()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toString()));
    }

    @PutMapping("/logs/level")
    public void setLevel(@RequestParam String logger, @RequestParam Level level) {
        if (logger == null || !StringUtils.hasText(logger)) {
            throw new IllegalArgumentException("logger name must not be null or empty");
        }

        if (!logger.startsWith("it.smartcommunitylab")) {
            throw new IllegalArgumentException("logger name must start with 'it.smartcommunitylab'");
        }

        if (level == null) {
            throw new IllegalArgumentException("log level must not be null");
        }

        log.debug("Setting log level for {} to {}", logger, level);
        context.getLogger(logger).setLevel(level);
        loggerLevels.put(logger, level);
    }

    @DeleteMapping("/logs/level")
    public void resetLogLevel(@RequestParam String logger) {
        if (!StringUtils.hasText(logger) || !logger.startsWith("it.smartcommunitylab")) {
            throw new IllegalArgumentException("logger name must start with 'it.smartcommunitylab'");
        }

        log.debug("Resetting log level for {}", logger);
        context.getLogger(logger).setLevel(null);
        loggerLevels.remove(logger);
    }
}
