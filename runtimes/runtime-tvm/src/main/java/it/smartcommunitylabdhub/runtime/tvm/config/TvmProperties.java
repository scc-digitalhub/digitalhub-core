/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TvmProperties {

    private Integer userId;
    private Integer groupId;
    private String homeDir;
    private String volumeSize;

    // Source format -> builder image.
    private Map<String, String> builders;

    private String compiler;

    private String serve;

    private String entrypoint;
    private Map<String, String> builderScripts;
}
