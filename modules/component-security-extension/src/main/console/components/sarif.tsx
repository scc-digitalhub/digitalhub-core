import { useEffect, useState } from 'react';
import {
    Alert,
    Badge,
    Box,
    Button,
    Chip,
    FormControl,
    InputLabel,
    LinearProgress,
    Link as MuiLink,
    MenuItem,
    Select,
    Stack,
    Typography,
} from '@mui/material';
import {
    ColumnsButton,
    DataTable,
    FunctionField,
    ListContextProvider,
    Pagination,
    ResourceContextProvider,
    TopToolbar,
    useList,
    useTranslate,
} from 'react-admin';
import { DownloadButton } from '@digitalhub/console/features/files/download/components/DownloadButton';
import { useDownload } from '@digitalhub/console/features/files/download/useDownload';
import {
    ReportError,
    reportErrorMessage,
    SecurityReport,
} from './securityReport';

interface SarifReport {
    runs?: SarifRun[];
}

interface SarifRun {
    tool?: { driver?: SarifDriver };
    results?: SarifResult[];
}

interface SarifDriver {
    name?: string;
    fullName?: string;
    informationUri?: string;
    version?: string;
    semanticVersion?: string;
    rules?: SarifRule[];
}

interface SarifRule {
    id?: string;
    shortDescription?: { text?: string };
    helpUri?: string;
    properties?: Record<string, unknown>;
}

interface SarifResult {
    ruleId?: string;
    level?: string;
    message?: { text?: string };
    properties?: Record<string, unknown>;
    locations?: Array<{
        physicalLocation?: {
            artifactLocation?: { uri?: string };
        };
    }>;
}

interface SarifFinding {
    id: string;
    package: string;
    vulnerability: string;
    severity: string;
    status: string;
    installedVersion: string;
    fixedVersion: string;
    description: string;
    url?: string;
}

interface LibrarySummary {
    package: string;
    count: number;
}

export const SarifReport = ({ report }: { report: SecurityReport }) => {
    const download = useDownload();
    const translate = useTranslate();

    const [findings, setFindings] = useState<SarifFinding[] | null>(null);
    const [scanDriver, setScanDriver] = useState<SarifDriver>();
    const [error, setError] = useState<Error | string>();
    const [isLoading, setIsLoading] = useState(false);
    const [libraryFilter, setLibraryFilter] = useState('');
    const [severityFilter, setSeverityFilter] = useState('');
    const allFindings = findings || [];
    const libraryOptions = Array.from(
        new Set(
            allFindings.map(finding => finding.package || 'Unknown library')
        )
    ).sort((left, right) => left.localeCompare(right));
    const severityOptions = Array.from(
        new Set(allFindings.map(finding => finding.severity))
    ).sort((left, right) => severityRank(left) - severityRank(right));
    const filteredFindings = allFindings.filter(
        finding =>
            (!libraryFilter ||
                (finding.package || 'Unknown library') === libraryFilter) &&
            (!severityFilter || finding.severity === severityFilter)
    );
    const hasFilters = Boolean(libraryFilter || severityFilter);
    const listContext = useList({ data: filteredFindings, perPage: 25 });

    useEffect(() => {
        let active = true;
        setIsLoading(true);
        setError(undefined);
        setFindings(null);
        setScanDriver(undefined);

        const loadReport = async () => {
            try {
                const downloadInfo = await download({
                    resource: 'extensions',
                    id: report.id!,
                    sub: 'report.json',
                });
                if (!downloadInfo?.url) {
                    throw new ReportError('security.report.downloadError');
                }

                const response = await fetch(downloadInfo.url);
                if (!response.ok) {
                    throw new ReportError('security.report.loadError', {
                        status: response.status,
                    });
                }

                const sarifReport = (await response.json()) as SarifReport;
                if (active) {
                    setFindings(parseSarifFindings(sarifReport));
                    setScanDriver(
                        sarifReport.runs?.find(run => run.tool?.driver)?.tool
                            ?.driver
                    );
                }
            } catch (loadError) {
                if (active) {
                    setError(
                        loadError instanceof Error
                            ? loadError
                            : String(loadError)
                    );
                }
            } finally {
                if (active) setIsLoading(false);
            }
        };

        void loadReport();
        return () => {
            active = false;
        };
    }, [download, report.id]);

    if (!report?.id) {
        return (
            <Alert severity="info">
                {translate('security.report.noReport', { format: 'SARIF' })}
            </Alert>
        );
    }

    console.log(report.id);

    const downloadToolbar = (
        <TopToolbar>
            <DownloadButton
                resource="extensions"
                id={report.id}
                sub={`report.json`}
            />
        </TopToolbar>
    );

    if (isLoading) {
        return (
            <Stack spacing={1}>
                {downloadToolbar}
                <LinearProgress />
            </Stack>
        );
    }
    if (error) {
        return (
            <Stack spacing={1}>
                {downloadToolbar}
                <Alert severity="error">
                    {reportErrorMessage(error, translate)}
                </Alert>
            </Stack>
        );
    }
    if (findings === null) {
        return (
            <Stack spacing={1}>
                {downloadToolbar}
                <LinearProgress />
            </Stack>
        );
    }
    if (findings.length === 0) {
        return (
            <Stack spacing={1}>
                {downloadToolbar}
                <ScanSummary driver={scanDriver} />
                <Alert severity="success">
                    {translate('security.report.noVulnerabilitiesFound')}
                </Alert>
            </Stack>
        );
    }

    const librarySummaries = Object.values(
        findings.reduce<Record<string, LibrarySummary>>(
            (libraries, finding) => {
                const packageName = finding.package || 'Unknown library';
                const summary = libraries[packageName] || {
                    package: packageName,
                    count: 0,
                };
                summary.count += 1;
                libraries[packageName] = summary;
                return libraries;
            },
            {}
        )
    ).sort((left, right) => left.package.localeCompare(right.package));

    return (
        <Stack spacing={0}>
            {downloadToolbar}
            <ScanSummary driver={scanDriver} />
            <Box
                component="section"
                aria-labelledby="library-summary-title"
                mt={2}
            >
                <Typography id="library-summary-title" variant="h6">
                    {translate('security.report.librariesCount', {
                        count: librarySummaries.length,
                    })}
                </Typography>
                <Stack direction="row" flexWrap="wrap" gap={1.5} py={2}>
                    {librarySummaries.map(library => (
                        <Badge
                            key={library.package}
                            badgeContent={library.count}
                            color="error"
                            overlap="rectangular"
                            sx={{ mr: 1, mb: 1 }}
                        >
                            <Chip
                                label={
                                    library.package === 'Unknown library'
                                        ? translate(
                                              'security.report.unknownLibrary'
                                          )
                                        : library.package
                                }
                            />
                        </Badge>
                    ))}
                </Stack>
            </Box>
            <Box
                component="section"
                aria-labelledby="vulnerability-list-title"
                mt={2}
            >
                <Stack
                    direction="row"
                    alignItems="center"
                    justifyContent="space-between"
                    sx={{ mb: 1 }}
                >
                    <Typography id="vulnerability-list-title" variant="h6">
                        {translate('security.report.vulnerabilitiesCount', {
                            count: filteredFindings.length,
                        })}
                    </Typography>
                    <ColumnsButton
                        storeKey="security-findings"
                        color="secondary"
                    />
                </Stack>
                <Stack
                    direction={{ xs: 'column', sm: 'row' }}
                    alignItems={{ xs: 'stretch', sm: 'center' }}
                    spacing={1.5}
                    component="section"
                    aria-label={translate(
                        'security.report.filterVulnerabilities'
                    )}
                >
                    <FormControl size="small" sx={{ minWidth: 190 }}>
                        <InputLabel id="sarif-library-filter-label">
                            {translate('security.report.labels.library')}
                        </InputLabel>
                        <Select<string>
                            labelId="sarif-library-filter-label"
                            value={libraryFilter}
                            label={translate('security.report.labels.library')}
                            onChange={event =>
                                setLibraryFilter(event.target.value)
                            }
                        >
                            <MenuItem value="">
                                {translate('security.report.allLibraries')}
                            </MenuItem>
                            {libraryOptions.map(library => (
                                <MenuItem key={library} value={library}>
                                    {library === 'Unknown library'
                                        ? translate(
                                              'security.report.unknownLibrary'
                                          )
                                        : library}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ minWidth: 160 }}>
                        <InputLabel id="sarif-severity-filter-label">
                            {translate('security.report.labels.severity')}
                        </InputLabel>
                        <Select<string>
                            labelId="sarif-severity-filter-label"
                            value={severityFilter}
                            label={translate('security.report.labels.severity')}
                            onChange={event =>
                                setSeverityFilter(event.target.value)
                            }
                        >
                            <MenuItem value="">
                                {translate('security.report.allSeverities')}
                            </MenuItem>
                            {severityOptions.map(severity => (
                                <MenuItem key={severity} value={severity}>
                                    {translate(
                                        `security.report.severities.${severity.toLowerCase()}`
                                    )}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    {hasFilters && (
                        <Button
                            size="small"
                            onClick={() => {
                                setLibraryFilter('');
                                setSeverityFilter('');
                            }}
                        >
                            {translate('security.report.clearFilters')}
                        </Button>
                    )}
                </Stack>
                {filteredFindings.length === 0 ? (
                    <Alert severity="info">
                        {translate('security.report.noVulnerabilitiesMatch')}
                    </Alert>
                ) : (
                    <ResourceContextProvider value="security-findings">
                        <ListContextProvider value={listContext}>
                            <DataTable
                                resource="security-findings"
                                storeKey="security-findings"
                                bulkActionButtons={false}
                                rowClick={false}
                                hiddenColumns={[
                                    'installedVersion',
                                    'fixedVersion',
                                    'status',
                                ]}
                            >
                                <DataTable.Col
                                    source="package"
                                    label={translate(
                                        'security.report.labels.library'
                                    )}
                                />
                                <DataTable.Col
                                    source="vulnerability"
                                    label={translate(
                                        'security.report.labels.vulnerability'
                                    )}
                                />
                                <DataTable.Col
                                    source="severity"
                                    label={translate(
                                        'security.report.labels.severity'
                                    )}
                                >
                                    <FunctionField
                                        render={(finding: SarifFinding) => (
                                            <Chip
                                                size="small"
                                                color={severityColor(
                                                    finding.severity
                                                )}
                                                label={translate(
                                                    `security.report.severities.${finding.severity.toLowerCase()}`
                                                )}
                                            />
                                        )}
                                    />
                                </DataTable.Col>
                                <DataTable.Col
                                    source="status"
                                    label={translate(
                                        'security.report.labels.status'
                                    )}
                                >
                                    <FunctionField
                                        render={(finding: SarifFinding) =>
                                            ['fixed', 'affected'].includes(
                                                finding.status
                                            )
                                                ? translate(
                                                      `security.report.statuses.${finding.status}`
                                                  )
                                                : finding.status
                                        }
                                    />
                                </DataTable.Col>
                                <DataTable.Col
                                    source="installedVersion"
                                    label={translate(
                                        'security.report.labels.installedVersion'
                                    )}
                                />
                                <DataTable.Col
                                    source="fixedVersion"
                                    label={translate(
                                        'security.report.labels.fixedVersion'
                                    )}
                                />
                                <DataTable.Col
                                    source="description"
                                    label={translate(
                                        'security.report.labels.description'
                                    )}
                                    disableSort
                                >
                                    <FunctionField
                                        render={(finding: SarifFinding) => (
                                            <>
                                                {finding.description}
                                                {finding.url && (
                                                    <>
                                                        {' '}
                                                        <MuiLink
                                                            href={finding.url}
                                                            target="_blank"
                                                            rel="noopener noreferrer"
                                                        >
                                                            {finding.url}
                                                        </MuiLink>
                                                    </>
                                                )}
                                            </>
                                        )}
                                    />
                                </DataTable.Col>
                            </DataTable>
                            <Pagination rowsPerPageOptions={[10, 25, 50]} />
                        </ListContextProvider>
                    </ResourceContextProvider>
                )}
            </Box>
        </Stack>
    );
};

const ScanSummary = (props: { driver?: SarifDriver }) => {
    const { driver } = props;
    const translate = useTranslate();
    if (!driver) return null;

    return (
        <Box component="section" aria-labelledby="scan-summary-title">
            <Typography id="scan-summary-title" variant="h6" mb={1}>
                {translate('security.report.labels.summary')}
            </Typography>
            <Stack
                direction={{ xs: 'column', sm: 'row' }}
                flexWrap="wrap"
                columnGap={3}
                rowGap={0.5}
            >
                {(driver.fullName || driver.name) && (
                    <Typography>
                        <strong>
                            {translate('security.report.labels.scanner')}:
                        </strong>{' '}
                        {driver.fullName || driver.name}
                    </Typography>
                )}
                {driver.name && driver.fullName && (
                    <Typography>
                        <strong>
                            {translate('security.report.labels.tool')}:
                        </strong>{' '}
                        {driver.name}
                    </Typography>
                )}
                {(driver.semanticVersion || driver.version) && (
                    <Typography>
                        <strong>
                            {translate('security.report.labels.version')}:
                        </strong>{' '}
                        {driver.semanticVersion || driver.version}
                    </Typography>
                )}
                {driver.informationUri && (
                    <Typography>
                        <strong>
                            {translate('security.report.labels.information')}:
                        </strong>{' '}
                        <MuiLink
                            href={driver.informationUri}
                            target="_blank"
                            rel="noopener noreferrer"
                        >
                            {driver.informationUri}
                        </MuiLink>
                    </Typography>
                )}
            </Stack>
        </Box>
    );
};

const parseSarifFindings = (report: SarifReport): SarifFinding[] =>
    (report.runs || []).flatMap((run, runIndex) => {
        const rules = new Map<string, SarifRule>();
        for (const rule of run.tool?.driver?.rules || []) {
            if (rule.id && !rules.has(rule.id)) rules.set(rule.id, rule);
        }
        return (run.results || []).map((result, resultIndex) => {
            const rule = result.ruleId ? rules.get(result.ruleId) : undefined;
            const properties = result.properties || {};
            const ruleProperties = rule?.properties;
            const artifactUri =
                result.locations?.[0]?.physicalLocation?.artifactLocation?.uri;
            const packageMatch = artifactUri
                ? /^pkg:[^/]+\/([^@?#]+)@([^?#]+)/.exec(artifactUri)
                : undefined;
            const fixedVersion =
                getProperty(properties, [
                    'FixedVersion',
                    'fixedVersion',
                    'fixVersion',
                ]) || getMessageField(result.message?.text, 'Fixed Version');
            const severity = getSeverity(
                getMessageField(result.message?.text, 'Severity') ||
                    getProperty(properties, ['Severity', 'severity']) ||
                    getTagSeverity(properties.tags) ||
                    getProperty(ruleProperties, ['Severity', 'severity']) ||
                    getTagSeverity(ruleProperties?.tags),
                getProperty(properties, [
                    'security-severity',
                    'cvssv3_baseScore',
                    'cvssv2_score',
                ]) ||
                    getProperty(ruleProperties, [
                        'security-severity',
                        'cvssv3_baseScore',
                        'cvssv2_score',
                    ]),
                result.level
            );

            return {
                id: `${runIndex}-${resultIndex}`,
                package:
                    getProperty(properties, [
                        'PkgName',
                        'packageName',
                        'package',
                        'component',
                    ]) ||
                    getMessageField(result.message?.text, 'Package') ||
                    packageMatch?.[1] ||
                    artifactUri?.split('/').pop() ||
                    '',
                vulnerability:
                    getProperty(properties, [
                        'VulnerabilityID',
                        'vulnerabilityId',
                    ]) ||
                    result.ruleId ||
                    '',
                severity,
                status:
                    getProperty(properties, ['Status', 'status']) ||
                    (fixedVersion ? 'fixed' : 'affected'),
                installedVersion:
                    getProperty(properties, [
                        'InstalledVersion',
                        'installedVersion',
                        'packageVersion',
                    ]) ||
                    getMessageField(
                        result.message?.text,
                        'Installed Version'
                    ) ||
                    packageMatch?.[2] ||
                    '',
                fixedVersion: fixedVersion || '',
                description:
                    getProperty(properties, ['Title', 'title']) ||
                    rule?.shortDescription?.text ||
                    result.message?.text ||
                    '',
                url:
                    getProperty(properties, [
                        'PrimaryURL',
                        'primaryUrl',
                        'url',
                    ]) ||
                    getReferenceUrl(properties.references) ||
                    rule?.helpUri,
            };
        });
    });

const getMessageField = (
    message: string | undefined,
    name: string
): string | undefined => {
    const prefix = `${name.toLowerCase()}:`;
    const line = message
        ?.split(/\r?\n/)
        .find(item => item.toLowerCase().startsWith(prefix));
    return line?.slice(prefix.length).trim() || undefined;
};

const getProperty = (
    properties: Record<string, unknown> | undefined,
    names: string[]
): string | undefined => {
    if (!properties) return undefined;
    for (const name of names) {
        const entry = Object.entries(properties).find(
            ([key]) => key.toLowerCase() === name.toLowerCase()
        );
        if (typeof entry?.[1] === 'string') return entry[1];
        if (typeof entry?.[1] === 'number' && Number.isFinite(entry[1])) {
            return String(entry[1]);
        }
    }
    return undefined;
};

const getTagSeverity = (tags: unknown): string | undefined => {
    if (!Array.isArray(tags)) return undefined;
    return tags.find(
        (tag): tag is string =>
            typeof tag === 'string' &&
            ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'].includes(
                tag.toUpperCase()
            )
    );
};

const getReferenceUrl = (references: unknown): string | undefined => {
    if (!Array.isArray(references)) return undefined;
    const reference = references.find(
        item => item && typeof item === 'object' && 'url' in item
    ) as { url?: unknown } | undefined;
    return typeof reference?.url === 'string' ? reference.url : undefined;
};

const getSeverity = (
    namedSeverity?: string,
    securitySeverity?: string,
    level?: string
): string => {
    const normalizedSeverity = namedSeverity?.toUpperCase();
    if (
        normalizedSeverity &&
        ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'].includes(
            normalizedSeverity
        )
    ) {
        return normalizedSeverity;
    }
    const score = Number(securitySeverity || namedSeverity);
    if (Number.isFinite(score) && score > 0) {
        if (score >= 9) return 'CRITICAL';
        if (score >= 7) return 'HIGH';
        if (score >= 4) return 'MEDIUM';
        return 'LOW';
    }
    const normalizedLevel = level?.toUpperCase();
    return ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'].includes(
        normalizedLevel || ''
    )
        ? normalizedLevel || 'UNKNOWN'
        : 'UNKNOWN';
};

const severityColor = (
    severity: string
): 'default' | 'error' | 'warning' | 'info' => {
    if (severity === 'CRITICAL' || severity === 'HIGH') return 'error';
    if (severity === 'MEDIUM') return 'warning';
    if (severity === 'LOW') return 'info';
    return 'default';
};

const severityRank = (severity: string): number => {
    const order = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'];
    const index = order.indexOf(severity);
    return index === -1 ? order.length : index;
};
