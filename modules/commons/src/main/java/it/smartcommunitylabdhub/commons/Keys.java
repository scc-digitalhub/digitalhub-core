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

package it.smartcommunitylabdhub.commons;

public class Keys {

    public static final long SERIAL_VERSION_UID = 100L;
    public static final String SLUG_PATTERN = "^[a-zA-Z0-9._+-]+$";
    public static final String FUNCTION_PATTERN = "([^:/]+)://([^/]+)/([^:]+):(.+)";
    public static final String WORKFLOW_PATTERN = "([^:/]+)://([^/]+)/([^:]+):(.+)";
    public static final String TASK_PATTERN = "([^:/]+)://([^/]+)/([^:]+)";
    public static final String KEY_PATTERN = "store://([^/]+)/([^/]+)/([^/]+)/([^:]+):(.+)";
    public static final String KEY_PATTERN_NO_ID = "store://([^/]+)/([^/]+)/([^/]+)/([^:]+)";
    public static final String STORE_PREFIX = "store://";
    public static final String PATH_DIVIDER = "/";
    public static final String ID_DIVIDER = ":";
    public static final String FILE_PATTERN = "(([^/]+)/)*([^/]+)";
    public static final String FOLDER_PATTERN = ".*\\/$";
    public static final String ZIP_PATTERN = ".*\\.zip$";
    public static final String CRONTAB_PATTERN =
        "((((\\d+,)+\\d+|(\\d+(\\/|-|#)\\d+)|\\d+L?|\\*(\\/\\d+)?|L(-\\d+)?|\\?|[A-Z]{3}(-[A-Z]{3})?) ?){5,7})|(@(annually|yearly|monthly|weekly|daily|hourly))";

    private Keys() {}
}
