import { defineConfig, Plugin } from "vite";
import react from "@vitejs/plugin-react";
import { splitVendorChunkPlugin } from "vite";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";
import fs from "node:fs";

const ROOT = __dirname;
const CONSOLE_ROOT = path.resolve(ROOT, "frontend/console");
const CONSOLE_SRC = path.resolve(CONSOLE_ROOT, "src");
// const COMPONENT_TEMPLATES_CONSOLE = path.resolve(
//   ROOT,
//   "modules/component-templates/src/main/console",
// );

const VIRTUAL_MODULE_ID = "virtual:digitalhub-console-extensions";
const VIRTUAL_EXTENSION_PREFIX = "virtual:digitalhub-console-extension:";
const RESOLVED_VIRTUAL_MODULE_ID = "\0" + VIRTUAL_MODULE_ID;
const RESOLVED_VIRTUAL_EXTENSION_PREFIX = "\0" + VIRTUAL_EXTENSION_PREFIX;

const VIRTUAL_BOOTSTRAP_ID = "virtual:digitalhub-console-extension-bootstrap";
const RESOLVED_VIRTUAL_BOOTSTRAP_ID = "\0" + VIRTUAL_BOOTSTRAP_ID;

/**
 * Find frontend contributions supplied by first-party Maven modules.
 *
 * Supported locations:
 *
 *   modules/<module>/src/main/console
 *   runtimes/<runtime>/src/main/console
 *   extensions/<extension>/src/main/console
 *   providers/<provider>/src/main/console
 */
function findConsoleContributions(): ConsoleContribution[] {
  const roots = ["modules", "runtimes", "extensions", "providers"];

  return roots.flatMap((root) => {
    const directory = path.resolve(ROOT, root);

    if (!fs.existsSync(directory)) {
      return [];
    }

    return fs
      .readdirSync(directory, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => {
        const contributionDirectory = path.resolve(
          directory,
          entry.name,
          "src",
          "main",
          "console",
        );

        return {
          id: entry.name,
          directory: contributionDirectory,
          entrypoint: path.join(contributionDirectory, "index.ts"),
        };
      })
      .filter((entry) => fs.existsSync(entry.entrypoint));
  });
}
interface ConsoleContribution {
  id: string;
  directory: string;
  entrypoint: string;
}
const consoleExtensionDiscovery: Plugin = {
  name: "digitalhub-console-extension-discovery",

  resolveId(id) {
    if (id === VIRTUAL_MODULE_ID) {
      return RESOLVED_VIRTUAL_MODULE_ID;
    }
    if (id === VIRTUAL_BOOTSTRAP_ID) {
      return RESOLVED_VIRTUAL_BOOTSTRAP_ID;
    }
    if (id.startsWith(VIRTUAL_EXTENSION_PREFIX)) {
      return (
        RESOLVED_VIRTUAL_EXTENSION_PREFIX +
        id.slice(VIRTUAL_EXTENSION_PREFIX.length)
      );
    }
  },

  load(id) {
    if (id === RESOLVED_VIRTUAL_BOOTSTRAP_ID) {
      return `
import { consoleExtensionRegistry } from "@digitalhub/console/features/extensions/ConsoleExtensionRegistry";
import { consoleExtensionModules } from "${VIRTUAL_MODULE_ID}";

for (const module of consoleExtensionModules) {
    consoleExtensionRegistry.registerModule(module);
}
`;
    }
    if (id === RESOLVED_VIRTUAL_MODULE_ID) {
      const contributions = findConsoleContributions();

      return `
export const consoleExtensionModules = [
${contributions
  .map(
    ({ id }) => `  {
    id: ${JSON.stringify(id)},
    load: () =>
      import(${JSON.stringify(VIRTUAL_EXTENSION_PREFIX + id)}).then((module) => module.default),
  }`,
  )
  .join(",\n")}
];
`;
    }

    if (id.startsWith(RESOLVED_VIRTUAL_EXTENSION_PREFIX)) {
      const moduleId = id.slice(RESOLVED_VIRTUAL_EXTENSION_PREFIX.length);

      const contribution = findConsoleContributions().find(
        (entry) => entry.id === moduleId,
      );

      if (!contribution) {
        throw new Error(`Unknown Console extension module: ${moduleId}`);
      }

      return `
export { default } from ${JSON.stringify(contribution.entrypoint)};
`;
    }
  },
  transformIndexHtml: {
    order: "pre",
    handler(html) {
      return {
        html,
        tags: [
          {
            tag: "script",
            attrs: {
              type: "module",
            },
            children: `import "${VIRTUAL_BOOTSTRAP_ID}";`,
            injectTo: "head-prepend",
          },
        ],
      };
    },
  },
};

/*
 * Monaco configuration copied from frontend/console.
 *
 * These should eventually be shared with the Console's own Vite config,
 * but keeping the first version independent means frontend/console remains
 * completely standalone.
 */

const MONACO_LANGUAGES_KEEP = new Set(["sql", "json", "yaml"]);

const monacoUnusedWorkersStub: Plugin = {
  name: "monaco-unused-workers-stub",

  load(id: string) {
    if (
      id.includes("/monaco-editor/esm/vs/language/") &&
      id.endsWith("/monaco.contribution.js") &&
      !id.includes("/language/json/")
    ) {
      return "";
    }
  },
};

const monacoBasicLanguagesStub: Plugin = {
  name: "monaco-basic-languages-stub",

  load(id: string) {
    if (id.includes("/basic-languages/_.contribution")) {
      return;
    }

    if (
      id.includes("/monaco-editor/esm/vs/basic-languages/") &&
      id.endsWith(".contribution.js")
    ) {
      const lang = id.split("/basic-languages/")[1]?.split("/")[0];

      if (lang && !MONACO_LANGUAGES_KEEP.has(lang)) {
        return "";
      }
    }
  },
};

const consoleContributions = findConsoleContributions();

export default defineConfig({
  /*
   * The existing Console remains the Vite application root.
   *
   * This is important because frontend/console/index.html and all of
   * the Console's existing relative paths continue to work.
   */
  root: CONSOLE_ROOT,

  plugins: [
    react(),
    splitVendorChunkPlugin(),
    tailwindcss(),
    consoleExtensionDiscovery,
    monacoUnusedWorkersStub,
    monacoBasicLanguagesStub,
  ],

  define: {
    "process.env": process.env,
  },

  resolve: {
    alias: {
      /*
       * First-party modules can depend on Console code without
       * knowing where the Console checkout lives.
       *
       * Example:
       *
       *   import StopButton
       *     from '@digitalhub/console/components/StopButton';
       */
      "@digitalhub/console": CONSOLE_SRC,
      // "@digitalhub/component-templates": COMPONENT_TEMPLATES_CONSOLE,
    },

    /*
     * Ensure the integrated application has one React instance even
     * when source is imported from outside frontend/console.
     */
    dedupe: ["react", "react-dom"],
  },

  server: {
    host: true,

    fs: {
      /*
       * The Vite root is frontend/console, but first-party modules
       * live outside it.
       */
      allow: [
        ROOT,
        CONSOLE_ROOT,
        // ...consoleContributions.map(({ directory }) => directory),
      ],
    },
  },

  base: "./",

  build: {
    /*
     * Unlike the standalone Console build, the integrated build goes
     * somewhere owned by the Core build.
     */
    outDir: path.resolve(ROOT, "target/console/dist"),

    /*
     * Vite normally refuses to empty an outDir outside the root.
     */
    emptyOutDir: true,

    manifest: true,
  },

  optimizeDeps: {
    include: ["@mui/material/Tooltip"],
    exclude: ["js-big-decimal"],
  },
});
