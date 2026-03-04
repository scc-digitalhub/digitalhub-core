/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.core.components.infrastructure.specs;

import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.services.SpecValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.validation.DataBinder;
import org.springframework.validation.SmartValidator;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Component
@Slf4j
public class SpecValidatorImpl implements SpecValidator {

    private SmartValidator validator;

    public SpecValidatorImpl(SmartValidator validator) {
        Assert.notNull(validator, "validator is required");
        this.validator = validator;
    }

    @Override
    public void validateSpec(Spec spec) throws IllegalArgumentException {
        // check with validator
        if (validator != null) {
            DataBinder binder = new DataBinder(spec);
            validator.validate(spec, binder.getBindingResult());
            if (binder.getBindingResult().hasErrors()) {
                try {
                    MethodParameter methodParameter = new MethodParameter(
                        this.getClass().getMethod("validateSpec", Spec.class),
                        0
                    );
                    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                        methodParameter,
                        binder.getBindingResult()
                    );
                    throw new IllegalArgumentException(ex.getMessage());
                } catch (NoSuchMethodException | SecurityException ex) {
                    StringBuilder sb = new StringBuilder();
                    binder
                        .getBindingResult()
                        .getFieldErrors()
                        .forEach(e -> {
                            sb.append(e.getField()).append(" ").append(e.getDefaultMessage()).append(", ");
                        });
                    String errorMsg = sb.toString();
                    throw new IllegalArgumentException(errorMsg);
                }
            }
        }
    }
}
