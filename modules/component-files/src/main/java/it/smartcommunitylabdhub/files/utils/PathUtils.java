/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.files.utils;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Pure functions on store paths (ex. s3://bucket/project/artifact/name/id/file.csv), used to decide
 * file ownership. Paths with relative segments ("..") are never accepted.
 */
public final class PathUtils {

    private static final String SCHEME_SEPARATOR = "://";

    private PathUtils() {}

    /**
     * Normalize a path: lowercase scheme, repeated slashes collapsed, "." segments removed, trailing
     * slash kept.
     *
     * @return the normalized path, or null when empty, without scheme or with ".." segments
     */
    @Nullable
    public static String normalize(@Nullable String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }

        int idx = path.indexOf(SCHEME_SEPARATOR);
        if (idx <= 0) {
            //only full paths (with a store scheme) are considered
            return null;
        }

        String scheme = path.substring(0, idx).toLowerCase();
        String rest = path.substring(idx + SCHEME_SEPARATOR.length());
        boolean trailing = rest.endsWith("/");

        List<String> segments = new ArrayList<>();
        for (String segment : rest.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                return null;
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            return null;
        }

        return scheme + SCHEME_SEPARATOR + String.join("/", segments) + (trailing ? "/" : "");
    }

    /**
     * True when the path is the folder itself (with or without trailing slash) or anything below it.
     * Segment boundaries are respected: "s3://b/prj2/x" is not within "s3://b/prj/". Different stores
     * or schemes never match.
     */
    public static boolean isWithin(@Nullable String path, @Nullable String folder) {
        String p = normalize(path);
        String f = normalize(folder);
        if (p == null || f == null) {
            return false;
        }

        String prefix = f.endsWith("/") ? f : f + "/";
        String self = prefix.substring(0, prefix.length() - 1);
        return p.equals(self) || p.startsWith(prefix);
    }
}
