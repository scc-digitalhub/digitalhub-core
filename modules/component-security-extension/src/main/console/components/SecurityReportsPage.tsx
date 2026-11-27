// SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
//
// SPDX-License-Identifier: Apache-2.0

import { useTranslate, useRecordContext, ResourceContextProvider, RecordContextProvider } from 'react-admin';
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Alert,
    Container,
    Stack,
    Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

import { PageTitle } from '@digitalhub/console/common/components/layout/PageTitle';
import { SecurityIcon } from './icon';
import { ShowBaseLive } from '@digitalhub/console/features/notifications/components/ShowBaseLive';
import { SarifReport } from './sarif';
import { SpdxReport } from './spdx';
import { CycloneDxReport } from './cyclonedx';
import { SecurityReport } from './securityReport';

const Report = ({ report }: { report: SecurityReport }) => {
    const translate = useTranslate();
    const rawFormat = report.spec?.format;
    const format =
        typeof rawFormat === 'string' ? rawFormat.trim().toLowerCase() : '';
    switch (format) {
        case 'sarif':
            return <SarifReport report={report} />;
        case 'spdx':
            return <SpdxReport report={report} />;
        case 'cyclonedx':
            return <CycloneDxReport report={report} />;
        default:
            return (
                <Alert severity="warning">
                    {format
                        ? translate('security.report.unsupportedFormat', {
                              format: report.spec?.format,
                          })
                        : translate('security.report.missingFormat')}
                </Alert>
            );
    }
};

const SecurityReports = () => {
    const record = useRecordContext();
    const translate = useTranslate();
    const extensions: SecurityReport[] = Array.isArray(record?.extensions)
        ? record.extensions
        : [];
    const reportExtensions = extensions.filter(
        extension => extension?.kind === 'security-report'
    );

    return (
        <Stack spacing={2}>
            {reportExtensions.map((extension, index) => (
                <RecordContextProvider value={extension}>
                <Accordion
                    key={
                        extension.id || extension.key || extension.name || index
                    }
                >
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                        <Typography>
                            {extension.spec?.analyzer ||
                                translate('security.report.unknownScanner')}
                        </Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                        <Report report={extension} />
                    </AccordionDetails>
                </Accordion>
                </RecordContextProvider>
            ))}
        </Stack>
    );
};

export const SecurityReportsPage = () => {
    const translate = useTranslate();

    return (
        <Container maxWidth={false} sx={{ pb: 2 }}>
            <ShowBaseLive>
                <>
                    <PageTitle
                        text={translate('security.pages.report.title')}
                        secondaryText={translate(
                            'security.pages.report.description'
                        )}
                        icon={<SecurityIcon fontSize="large" />}
                    />
                    <SecurityReports />
                </>
            </ShowBaseLive>
        </Container>
    );
};
