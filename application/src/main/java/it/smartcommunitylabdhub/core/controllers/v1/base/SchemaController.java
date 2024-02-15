package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/schemas")
@ApiVersion("v1")
@Validated
@Tag(name = "Schema base API", description = "Endpoints related to spec schemas")
public class SchemaController {

    @Autowired
    private SpecRegistry specRegistry;

    @Operation(
        summary = "List entity schemas",
        description = "Return a list of all the spec schemas available for the given entity"
    )
    @GetMapping(path = "/{entity}")
    public ResponseEntity<Page<Schema>> listSchemas(
        @PathVariable @Valid @NotNull EntityName entity,
        @RequestParam(required = false) Optional<String> runtime,
        Pageable pageable
    ) {
        Collection<Schema> schemas;
        if (runtime.isPresent()) {
            schemas = specRegistry.getSchemas(entity, runtime.get());
        } else {
            schemas = specRegistry.listSchemas(entity);
        }
        PageImpl<Schema> page = new PageImpl<>(new ArrayList<>(schemas), pageable, schemas.size());

        return ResponseEntity.ok(page);
    }

    @GetMapping(path = "/{entity}/{kind}", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Schema> getSchema(@PathVariable @Valid @NotNull EntityName entity, @NotBlank String kind) {
        Schema schema = specRegistry.getSchema(kind, entity);

        return ResponseEntity.ok(schema);
    }
}
