package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.components.logs.InMemoryLogAppender;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
public class AdminController {

    @Autowired
    private InMemoryLogAppender inMemoryLogAppender;

    @Operation(summary = "List logs", description = "Return a list of recent logs")
    @GetMapping(path = "/logs", produces = "application/json; charset=UTF-8")
    public List<InMemoryLogAppender.LogEntry> getLogs() {
        return inMemoryLogAppender.recent();
    }
}
