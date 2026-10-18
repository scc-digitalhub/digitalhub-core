// SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
//
// SPDX-License-Identifier: Apache-2.0

import {
    useRecordContext,
    useTranslate,
    useDataProvider,
    useResourceContext,
    useAuthProvider,
} from 'react-admin';
import { createServices } from './services';
import {
    ComplianceServicesProvider,
    DatasetComplianceWidget
} from "./compliance-widgets";
import { PageTitle } from '@digitalhub/console/common/components/layout/PageTitle';
import { ComplianceIcon } from './icon';
 
export const AiComplianceDataPage = (props: {
    record?: any;
    source?: string;
}) => {
    const translate = useTranslate();
    const dataProvider = useDataProvider(); 
    const authProvider = useAuthProvider();
    const record = useRecordContext(props);
    const resource = useResourceContext(); 


    return (
        <ComplianceServicesProvider services={createServices(dataProvider, authProvider)}>
            <PageTitle
                text={translate('compliance.pages.datacompliance.title')}
                secondaryText={translate(
                    'compliance.pages.datacompliance.description'
                )}
                icon={<ComplianceIcon fontSize="large" />}
            />

            <DatasetComplianceWidget
                entity={record}
                resource={resource as string}
            />
        </ComplianceServicesProvider>
    );
};