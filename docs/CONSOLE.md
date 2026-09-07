# Console Build And Run Guide

This document explains how the DigitalHub Console frontend is built from the repository root and how to run it with a local environment file.

## Overview

The Console source code lives in frontend/console, but the integrated development and build flow is driven by the top-level Vite configuration at repository root.

Main points:

- Root Vite config: vite.config.mts
- Vite app root is set to frontend/console
- The integrated build output goes to target/console/dist
- Root Vite adds custom extension discovery and loading across top-level folders

## Top-Level Vite Integration

The root Vite config does all of the following:

- Uses frontend/console as Vite root.
- Keeps Console source importable via alias @digitalhub/console.
- Enables out-of-tree source access for modules, runtimes, extensions, and providers.
- Injects a bootstrap virtual module that registers discovered extension modules.
- Loads extension modules lazily through dynamic import.

Extension discovery convention:

- modules/<name>/src/main/console/index.ts
- runtimes/<name>/src/main/console/index.ts
- extensions/<name>/src/main/console/index.ts
- providers/<name>/src/main/console/index.ts

Each discovered entrypoint must default-export a ConsoleExtensionModule descriptor.

## Run Console With Root Vite

From repository root, run:

```bash
npx env-cmd -f .env.local vite
```

This starts Vite using the top-level vite.config.mts and applies environment variables from .env.local.

## Create .env.local

Create .env.local at repository root by copying the production template from frontend/console:

```bash
cp frontend/console/.env.production .env.local
```

Then edit .env.local for local development.

## Recommended .env.local Values

Start from the copied template and set at least these keys:

- REACT_APP_API_URL: local backend API base path or URL (for example /api/v1)
- REACT_APP_WEBSOCKET_URL: local websocket path or URL (for example /ws)
- REACT_APP_CONTEXT_PATH: console base path (for example /console)
- REACT_APP_AUTH_URL, REACT_APP_OAUTH2_ISSUER, REACT_APP_DHCORE_CLIENT_ID: auth settings based on your environment
- REACT_APP_DHCORE_PROXY: proxy mode or external proxy URL if used
- REACT_APP_TRINO_URL: optional, set only if SQL feature is enabled
- REACT_APP_TUTORIALS_URL: optional tutorials feed URL
- VITE_APP_NAME: app title shown in browser

If needed, disable optional features in local setup:

- REACT_APP_ENABLE_SOLR=false
- REACT_APP_ENABLE_METRICS=false

## Notes

- The root flow is intended for integrated development where Console imports out-of-tree extension code.
- Standalone frontend/console scripts still exist, but they do not include the root custom extension loader behavior.
