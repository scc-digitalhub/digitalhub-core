import { useEffect, useState } from 'react';
import {
    Alert,
    Box,
    LinearProgress,
    Stack,
    TextField,
    Typography,
} from '@mui/material';
import {
    DataTable,
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

export interface SbomComponent {
    id: string;
    name: string;
    version: string;
    license: string;
    purl: string;
}

export interface SbomVulnerability {
    id: string;
    vulnerability: string;
    component: string;
    severity: string;
    status: string;
    description: string;
}

export interface SbomDocument {
    format: string;
    version?: string;
    name?: string;
    components: SbomComponent[];
    vulnerabilities?: SbomVulnerability[];
}

export type JsonObject = Record<string, unknown>;

export const isObject = (value: unknown): value is JsonObject =>
    typeof value === 'object' && value !== null && !Array.isArray(value);

export const stringValue = (value: unknown): string =>
    typeof value === 'string' ? value : '';

export const objectArray = (value: unknown, field: string): JsonObject[] => {
    if (value === undefined) return [];
    if (!Array.isArray(value) || !value.every(isObject)) {
        throw new ReportError('security.report.invalidField', { field });
    }
    return value;
};

export const SbomReport = ({
    report,
    format,
    parse,
}: {
    report: SecurityReport;
    format: string;
    parse: (document: unknown) => SbomDocument;
}) => {
    const download = useDownload();
    const translate = useTranslate();
    const [result, setResult] = useState<{
        document?: SbomDocument;
        error?: Error | string;
    }>();

    useEffect(() => {
        if (!report?.id) return;

        let active = true;
        setResult(undefined);

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
                const parsed = parse(await response.json());
                if (active) setResult({ document: parsed });
            } catch (loadError) {
                if (active) {
                    setResult({
                        error:
                            loadError instanceof Error
                                ? loadError
                                : String(loadError),
                    });
                }
            }
        };

        void loadReport();
        return () => {
            active = false;
        };
    }, [download, report.id, parse]);

    if (!result) {
        return (
            <Alert severity="info">
                {translate('security.report.noReport', { format })}
            </Alert>
        );
    }
    return (
        <Stack spacing={2}>
            <TopToolbar>
                <DownloadButton
                    resource="extensions"
                    id={report.id}
                    sub="report.json"
                />
            </TopToolbar>
            {result?.error ? (
                <Alert severity="error">
                    {reportErrorMessage(result.error, translate)}
                </Alert>
            ) : result?.document ? (
                <SbomContents document={result.document} />
            ) : (
                <LinearProgress />
            )}
        </Stack>
    );
};

const SbomContents = ({ document }: { document: SbomDocument }) => {
    const translate = useTranslate();
    const [filter, setFilter] = useState('');
    const query = filter.trim().toLowerCase();
    const components = document.components.filter(component =>
        [
            component.name,
            component.version,
            component.license,
            component.purl,
        ].some(value => value.toLowerCase().includes(query))
    );
    const vulnerabilities = document.vulnerabilities?.filter(vulnerability =>
        [
            vulnerability.vulnerability,
            vulnerability.component,
            vulnerability.severity,
            vulnerability.status,
            vulnerability.description,
        ].some(value => value.toLowerCase().includes(query))
    );
    const componentList = useList({ data: components, perPage: 25 });
    const vulnerabilityList = useList({
        data: vulnerabilities || [],
        perPage: 25,
    });

    return (
        <Stack spacing={2}>
            <Box>
                <Typography variant="h6">
                    {translate('security.report.labels.summary')}
                </Typography>
                <Typography>
                    <strong>
                        {translate('security.report.labels.format')}:
                    </strong>{' '}
                    {document.format}
                    {document.version && ` ${document.version}`}
                </Typography>
                {document.name && (
                    <Typography>
                        <strong>
                            {translate('security.report.labels.name')}:
                        </strong>{' '}
                        {document.name}
                    </Typography>
                )}
            </Box>
            <TextField
                label={translate('security.report.filterReport')}
                size="small"
                value={filter}
                onChange={event => setFilter(event.target.value)}
            />
            <Box>
                <Typography variant="h6">
                    {translate('security.report.componentsCount', {
                        count: components.length,
                    })}
                </Typography>
                {components.length === 0 ? (
                    <Alert severity="info">
                        {translate(
                            query
                                ? 'security.report.noComponentsMatch'
                                : 'security.report.noComponents'
                        )}
                    </Alert>
                ) : (
                    <ResourceContextProvider value="security-components">
                        <ListContextProvider value={componentList}>
                            <DataTable
                                resource="security-components"
                                storeKey="security-components"
                                bulkActionButtons={false}
                                rowClick={false}
                            >
                                <DataTable.Col
                                    source="name"
                                    label={translate(
                                        'security.report.labels.component'
                                    )}
                                />
                                <DataTable.Col
                                    source="version"
                                    label={translate(
                                        'security.report.labels.version'
                                    )}
                                />
                                <DataTable.Col
                                    source="license"
                                    label={translate(
                                        'security.report.labels.license'
                                    )}
                                />
                                <DataTable.Col
                                    source="purl"
                                    label={translate(
                                        'security.report.labels.packageUrl'
                                    )}
                                />
                            </DataTable>
                            <Pagination rowsPerPageOptions={[10, 25, 50]} />
                        </ListContextProvider>
                    </ResourceContextProvider>
                )}
            </Box>
            <Box>
                <Typography variant="h6">
                    {vulnerabilities
                        ? translate('security.report.vulnerabilitiesCount', {
                              count: vulnerabilities.length,
                          })
                        : translate('security.report.vulnerabilities')}
                </Typography>
                {vulnerabilities === undefined ? (
                    <Alert severity="info">
                        {translate(
                            'security.report.vulnerabilityDataUnavailable'
                        )}
                    </Alert>
                ) : vulnerabilities.length === 0 ? (
                    <Alert severity="info">
                        {translate(
                            query
                                ? 'security.report.noVulnerabilitiesMatchFilter'
                                : 'security.report.noVulnerabilitiesListed'
                        )}
                    </Alert>
                ) : (
                    <ResourceContextProvider value="security-vulnerabilities">
                        <ListContextProvider value={vulnerabilityList}>
                            <DataTable
                                resource="security-vulnerabilities"
                                storeKey="security-vulnerabilities"
                                bulkActionButtons={false}
                                rowClick={false}
                            >
                                <DataTable.Col
                                    source="vulnerability"
                                    label={translate(
                                        'security.report.labels.vulnerability'
                                    )}
                                />
                                <DataTable.Col
                                    source="component"
                                    label={translate(
                                        'security.report.labels.component'
                                    )}
                                />
                                <DataTable.Col
                                    source="severity"
                                    label={translate(
                                        'security.report.labels.severity'
                                    )}
                                />
                                <DataTable.Col
                                    source="status"
                                    label={translate(
                                        'security.report.labels.status'
                                    )}
                                />
                                <DataTable.Col
                                    source="description"
                                    label={translate(
                                        'security.report.labels.description'
                                    )}
                                    disableSort
                                />
                            </DataTable>
                            <Pagination rowsPerPageOptions={[10, 25, 50]} />
                        </ListContextProvider>
                    </ResourceContextProvider>
                )}
            </Box>
        </Stack>
    );
};
