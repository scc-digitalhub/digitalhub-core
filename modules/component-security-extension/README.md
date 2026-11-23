# Security extension

Set `SECURITY_EXTENSION_ENABLED=true` to provision the configured analyzer functions in newly created projects. Existing projects are not modified. Analyzer definitions are read from `SECURITY_EXTENSION_ANALYZERS_PATH` (default: `classpath*:security/analyzers/`).

Each YAML definition supplies a `function` template and may supply a `trigger` template. A trigger's `key` can use `${project}`; the provisioner supplies the function reference and activates the trigger. Trigger run parameters belong in the trigger's `spec.template`.

The bundled container Trivy analyzer runs when a container image reaches `READY`. It uploads a SARIF report and derives the extension's findings and assessment from its results. Bandit and Python Trivy are provisioned without triggers for manual use: container-image records do not contain Python source, and the default Python runtime does not provide Trivy. Provisioning happens after project creation; failures are logged and may require manual recovery.

Security report extensions specify the report file in `spec.path` and its format in `spec.format` (`sarif`, `spdx`, or `cyclonedx`, case-insensitive). The console reads JSON reports; missing or unsupported formats are reported instead of being inferred. SARIF displays findings. SPDX 2.x and compact SPDX 3 JSON-LD display locally available packages; CycloneDX displays components and any vulnerabilities included in the BOM. An SBOM without displayed vulnerability data does not establish that the components are free of vulnerabilities.

The Trivy SPDX analyzer leaves assessment and findings unset when the SBOM contains no security advisory references. When present, each advisory reference is counted as an unknown-severity finding and the assessment is `WARNING`; SPDX advisory references do not provide severity. The analyzer does not enable vulnerability scanning when generating the SBOM.
