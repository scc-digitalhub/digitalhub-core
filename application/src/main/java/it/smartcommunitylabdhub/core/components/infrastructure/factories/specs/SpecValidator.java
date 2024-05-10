// Copyright 2024 mat
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package it.smartcommunitylabdhub.core.components.infrastructure.factories.specs;

import it.smartcommunitylabdhub.commons.models.specs.Spec;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.MethodArgumentNotValidException;

public interface SpecValidator {
    public void validateSpec(@NotNull Spec spec) throws MethodArgumentNotValidException, IllegalArgumentException;
}
