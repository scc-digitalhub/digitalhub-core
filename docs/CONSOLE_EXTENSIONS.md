# Console Extensions Guide

This guide explains how to build frontend extensions for the DigitalHub Console.

## 1. Extension Concept And Capabilities

A console extension is a TypeScript module exported as the default object from a discovered frontend entrypoint.

Current capabilities:

- Register reusable React components in a `components` map.
- Contribute components into standard Console views (`list`, `create`, `edit`, `show`) using `views` and `showIn`.
- Register JsonSchema integrations (`widgets`, `templates`, `fields`) through `jsonSchema`.

The extension descriptor type is defined in:

- `frontend/console/src/features/extensions/ConsoleExtension.ts`

## 2. How To Export A Component

The extension discovery convention expects one entrypoint in one of these locations:

- `modules/<module-name>/src/main/console/index.ts`
- `runtimes/<runtime-name>/src/main/console/index.ts`
- `extensions/<extension-name>/src/main/console/index.ts`
- `providers/<provider-name>/src/main/console/index.ts`

### Starter Template

Use this as a copy-paste starting point.

```ts
import { ConsoleExtensionModule } from "@digitalhub/console/features/extensions/ConsoleExtension";
import { MyToolbarButton } from "./components/MyToolbarButton";
import { MyCustomWidget } from "./components/MyCustomWidget";

const extensionModule: ConsoleExtensionModule = {
  // Optional. If omitted, the folder name is used as module id.
  id: "my-extension",

  // Local component registry.
  components: {
    toolbarButton: MyToolbarButton,
    customWidget: MyCustomWidget,
  },

  // Optional view contributions.
  views: {
    functions: {
      list: [
        {
          showIn: "toolbar",
          component: "toolbarButton",
          order: 20,
        },
      ],
    },
  },

  // Optional JsonSchema integrations.
  jsonSchema: {
    widgets: {
      my-custom-widget: "customWidget",
    },
  },
};

export default extensionModule;
```

Inside the entrypoint, export a default `ConsoleExtensionModule` object.

```ts
import { ConsoleExtensionModule } from "@digitalhub/console/features/extensions/ConsoleExtension";
import { HubButton } from "./components/HubButton";

const extensionModule: ConsoleExtensionModule = {
  // Optional. If omitted, the folder name is used.
  id: "component-templates",

  // Local component ids.
  components: {
    hubButton: HubButton,
  },
};

export default extensionModule;
```

Notes:

- Component ids in `components` are local ids (`hubButton`, `myCustomWidget`, etc.).
- The registry internally namespaces them with module id for runtime uniqueness.

## 3. Use A Component In Default Console Views

Use the `views` section to attach components to standard views and regions.

- Views: `list`, `create`, `edit`, `show`
- Regions (`showIn`): `toolbar`, `tab`, `section`

```ts
const extensionModule: ConsoleExtensionModule = {
  id: "component-templates",
  components: {
    hubButton: HubButton,
  },
  views: {
    functions: {
      list: [
        {
          showIn: "toolbar",
          component: "hubButton", // local component id
          // optional order
          order: 20,
          // optional explicit contribution id
          // id: "component-templates.functions.list.toolbar.hub"
        },
      ],
    },
  },
};
```

Ordering rules:

- `order` is optional.
- Contributions are sorted by `order` ascending.
- If `order` is missing, fallback sorting uses contribution `id`.

## 4. Use A Component As JsonSchema Widget/Template/Field

Declare JsonSchema integrations in `jsonSchema` by mapping the JsonSchema key to a local component id.

```ts
import { ConsoleExtensionModule } from "@digitalhub/console/features/extensions/ConsoleExtension";
import { HubButton } from "./components/HubButton";
import { HubBrowserWidget } from "./components/HubBrowserWidget";
import { HubSectionTemplate } from "./components/HubSectionTemplate";
import { HubSpecialField } from "./components/HubSpecialField";

const extensionModule: ConsoleExtensionModule = {
  id: "component-templates",
  components: {
    hubButton: HubButton,
    hubBrowserWidget: HubBrowserWidget,
    hubSectionTemplate: HubSectionTemplate,
    hubSpecialField: HubSpecialField,
  },
  views: {
    functions: {
      list: [{ showIn: "toolbar", component: "hubButton" }],
    },
  },
  jsonSchema: {
    widgets: {
      hubBrowser: "hubBrowserWidget",
    },
    templates: {
      HubSectionTemplate: "hubSectionTemplate",
    },
    fields: {
      HubSpecialField: "hubSpecialField",
    },
  },
};

export default extensionModule;
```

Important JsonSchema rules (by design):

- JsonSchema keys are used as-is. No key normalization is applied.
- If a key conflicts with an already registered extension key, loading fails with an error.
- If a key conflicts with a built-in Console JsonSchema key, loading fails with an error.
- If a mapped local component id does not exist in `components`, loading fails with an error.

## Runtime Notes

- Extensions are discovered by the root Vite integration config.
- Modules are loaded lazily by the extension controller/registry layer.
- View contributions and JsonSchema contributions are resolved from the registry at runtime.

## Troubleshooting

### Module is not discovered

Symptom:

- Your component never appears.

Checks:

- Verify the entrypoint path matches one of the supported conventions.
- Verify the file name is exactly `index.ts`.
- Verify the module exports a default `ConsoleExtensionModule` object.

### Module load fails with "Console extension module not found"

Symptom:

- Console logs an error while loading extensions.

Checks:

- Ensure the module folder name matches the discovered module id.
- Ensure the extension entrypoint compiles successfully.

### Contribution references an unknown component

Symptom:

- Error similar to: `Console extension component not found`.

Cause:

- A `views` or `jsonSchema` entry references a local component id not present in `components`.

Fix:

- Add the missing component to `components` or fix the referenced local id.

### Duplicate view contribution id

Symptom:

- Error similar to: `Console extension contribution already registered`.

Cause:

- Two contributions resolve to the same id.

Fix:

- Provide distinct explicit `id` values, or change positions so auto-generated ids differ.

### Duplicate JsonSchema key

Symptom:

- Error similar to:
  - `Console extension jsonSchema widget key already registered`
  - `Console extension jsonSchema template key already registered`
  - `Console extension jsonSchema field key already registered`

Cause:

- Another extension or built-in already uses the same key.

Fix:

- Choose a different key in `jsonSchema.widgets`, `jsonSchema.templates`, or `jsonSchema.fields`.
- Remember: JsonSchema keys are literal and are not normalized.

### View contribution does not render where expected

Symptom:

- Contribution exists but does not appear in UI.

Checks:

- Verify `resource` matches the current React-Admin resource name.
- Verify `view` matches the active view (`list`, `create`, `edit`, `show`).
- Verify `showIn` matches the target placement (`toolbar`, `tab`, `section`).
- If the host is outside standard RA context, pass explicit `resource` and `view` overrides.
