/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmTargetArchitecture;
import org.junit.jupiter.api.Test;

class TvmTargetArchitectureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // LLVM reads a three-part 32-bit ARM triple such as "armv7l-linux-gnueabihf" as
    // vendor "linux" without an EABI environment, and then calls the libgcc conversion
    // helpers with the wrong calling convention: the armv7l code computes wrong values.
    @Test
    void armv7lUsesTheFullGnuEabiHardFloatTriple() throws Exception {
        JsonNode target = MAPPER.readTree(TvmTargetArchitecture.armv7l.getValue());
        String triple = target.get("mtriple").asText();

        assertEquals(4, triple.split("-").length, triple);
        assertTrue(triple.endsWith("-linux-gnueabihf"), triple);
        assertEquals("hard", target.get("mfloat-abi").asText());
    }
}
