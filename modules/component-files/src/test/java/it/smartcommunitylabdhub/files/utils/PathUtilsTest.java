/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.files.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PathUtilsTest {

    private static final String FOLDER = "s3://bucket/prj/artifact/my-artifact/id1/";

    @Test
    void normalizeCollapsesSlashesAndDotSegments() {
        assertThat(PathUtils.normalize("S3://bucket//prj/./artifact/")).isEqualTo("s3://bucket/prj/artifact/");
        assertThat(PathUtils.normalize("s3://bucket/prj/file.csv")).isEqualTo("s3://bucket/prj/file.csv");
    }

    @Test
    void normalizeRejectsRelativeEmptyAndSchemeless() {
        assertThat(PathUtils.normalize("s3://bucket/prj/../other/")).isNull();
        assertThat(PathUtils.normalize("")).isNull();
        assertThat(PathUtils.normalize(null)).isNull();
        assertThat(PathUtils.normalize("bucket/prj/file.csv")).isNull();
        assertThat(PathUtils.normalize("s3://")).isNull();
    }

    @Test
    void insideTheFolder() {
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/id1/data.csv", FOLDER)).isTrue();
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/id1/sub/data.csv", FOLDER)).isTrue();
    }

    @Test
    void theFolderItselfWithOrWithoutSlash() {
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/id1/", FOLDER)).isTrue();
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/id1", FOLDER)).isTrue();
        assertThat(
            PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/id1/", "s3://bucket/prj/artifact/my-artifact/id1")
        ).isTrue();
    }

    @Test
    void outsideTheFolder() {
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/id2/data.csv", FOLDER)).isFalse();
        assertThat(PathUtils.isWithin("s3://bucket/prj/data.csv", FOLDER)).isFalse();
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/", FOLDER)).isFalse();
    }

    @Test
    void sharedPrefixIsNotWithin() {
        assertThat(PathUtils.isWithin("s3://bucket/prj2/artifact/my-artifact/id1/data.csv", FOLDER)).isFalse();
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact-2/id1/data.csv", FOLDER)).isFalse();
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/id10/data.csv", FOLDER)).isFalse();
    }

    @Test
    void relativeSegmentsAreNeverWithin() {
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/id1/../id2/data.csv", FOLDER)).isFalse();
        assertThat(PathUtils.isWithin("s3://bucket/prj/artifact/my-artifact/id1/../../other/x", FOLDER)).isFalse();
    }

    @Test
    void differentStoreOrSchemeIsNeverWithin() {
        assertThat(PathUtils.isWithin("s3://other/prj/artifact/my-artifact/id1/data.csv", FOLDER)).isFalse();
        assertThat(PathUtils.isWithin("gs://bucket/prj/artifact/my-artifact/id1/data.csv", FOLDER)).isFalse();
        assertThat(PathUtils.isWithin("https://bucket/prj/artifact/my-artifact/id1/data.csv", FOLDER)).isFalse();
    }
}
