// SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
//
// SPDX-License-Identifier: Apache-2.0

import {
    LoadingIndicator,
    useDataProvider,
    useAuthProvider,
    useRecordContext,
    useTranslate,
} from 'react-admin';
import { useRootSelector } from '@dslab/ra-root-selector';
import { createServices } from './services';


import {
    ComplianceServicesProvider, ProjectComplianceWidget,
} from "./compliance-widgets";
import { PageTitle } from '@digitalhub/console/common/components/layout/PageTitle';
import { ComplianceIcon } from './icon';
import { useContext, useEffect, useState } from 'react';
 
export const AiCompliancePage = () => {
    const dataProvider = useDataProvider();
    const authProvider = useAuthProvider();
    const { root: projectId } = useRootSelector();
    const [project, setProject] = useState<any>(null);
    const [extension, setExtension] = useState<any>(null);
    const translate = useTranslate();

    useEffect(() => {
        if (!dataProvider || !projectId) return;

        dataProvider.getOne('projects', { id: projectId }).then(res => {
            if (res.data) {
                setProject(res.data);
                setExtension(res.data.extensions?.find((e: any) => e.kind === 'ai-compliance'));
            }
        });

    }, [dataProvider, projectId]);

    if (!project) {
        return <LoadingIndicator />;
    }


    return (
        <ComplianceServicesProvider services={createServices(dataProvider, authProvider)}>
            <PageTitle
                text={translate('compliance.pages.compliance.title')}
                secondaryText={translate(
                    'compliance.pages.compliance.description'
                )}
                icon={<ComplianceIcon fontSize="large" />}
            />

            <ProjectComplianceWidget entity={project} resource="projects" extension={extension} />
        </ComplianceServicesProvider>
    );
};