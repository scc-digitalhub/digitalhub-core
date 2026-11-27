export const it = {
    security: {
        pages: {
            report: {
                title: 'Report di sicurezza',
                description:
                    'Visualizza i report di sicurezza per questa risorsa.',
            },
        },
        report: {
            labels: {
                summary: 'Riepilogo',
                scanner: 'Scanner',
                tool: 'Strumento',
                version: 'Versione',
                information: 'Informazioni',
                format: 'Formato',
                name: 'Nome',
                library: 'Libreria',
                vulnerability: 'Vulnerabilità',
                severity: 'Gravità',
                status: 'Stato',
                installedVersion: 'Versione installata',
                fixedVersion: 'Versione corretta',
                description: 'Descrizione',
                component: 'Componente',
                license: 'Licenza',
                packageUrl: 'URL del pacchetto',
            },
            severities: {
                critical: 'CRITICA',
                high: 'ALTA',
                medium: 'MEDIA',
                low: 'BASSA',
                unknown: 'SCONOSCIUTA',
            },
            statuses: {
                fixed: 'corretto',
                affected: 'vulnerabile',
            },
            unknownScanner: 'Scanner sconosciuto',
            unknownLibrary: 'Libreria sconosciuta',
            librariesCount: 'Librerie (%{count})',
            componentsCount: 'Componenti (%{count})',
            vulnerabilities: 'Vulnerabilità',
            vulnerabilitiesCount: 'Vulnerabilità (%{count})',
            filterVulnerabilities: 'Filtra vulnerabilità',
            filterReport: 'Filtra report',
            allLibraries: 'Tutte le librerie',
            allSeverities: 'Tutti i livelli di gravità',
            clearFilters: 'Cancella filtri',
            missingFormat:
                'Il formato del report di sicurezza non è specificato.',
            unsupportedFormat:
                'Formato del report di sicurezza non supportato: %{format}',
            noReport: 'Nessun report %{format} allegato.',
            noVulnerabilitiesFound: 'Nessuna vulnerabilità rilevata.',
            noVulnerabilitiesMatch:
                'Nessuna vulnerabilità corrisponde ai filtri selezionati.',
            noComponentsMatch: 'Nessun componente corrisponde al filtro.',
            noComponents:
                'Nessun componente disponibile nella vista del report.',
            vulnerabilityDataUnavailable:
                'I dati sulle vulnerabilità non sono disponibili in questa vista del report.',
            noVulnerabilitiesMatchFilter:
                'Nessuna vulnerabilità corrisponde al filtro.',
            noVulnerabilitiesListed:
                'Nessuna vulnerabilità elencata nel report.',
            downloadError: 'Impossibile scaricare il report di sicurezza.',
            loadError:
                'Impossibile caricare il report di sicurezza (%{status}).',
            invalidField: 'Campo %{field} non valido nel report di sicurezza.',
            invalidSpdx: 'Report JSON SPDX non valido.',
            invalidSpdxJsonLd:
                'Report JSON-LD SPDX non valido o non supportato.',
            unsupportedSpdx: 'Report JSON SPDX non valido o non supportato.',
            invalidCycloneDx: 'Report JSON CycloneDX non valido.',
        },
    },
};
