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
    ModelComplianceWidget
} from "./compliance-widgets";
import { PageTitle } from '@digitalhub/console/common/components/layout/PageTitle';
import { ComplianceIcon } from './icon';
import { useGetSchemas } from '@digitalhub/console/common/jsonSchema/schemaController';
 
export const AiComplianceModelPage = (props: {
    record?: any;
    source?: string;
}) => {
    const translate = useTranslate();
    const dataProvider = useDataProvider();
    const authProvider = useAuthProvider();
    const { source = 'extensions' } = props;
    const record = useRecordContext(props);
    const resource = useResourceContext(); 

    //check if any extension is available
    const { data: schemas, isLoading } = useGetSchemas('extensions');

    const field = record ? record[source] : null;
    if (!field || isLoading || !schemas) {
        return <></>;
    }

    return (
        <ComplianceServicesProvider services={createServices(dataProvider, authProvider)}>
            <PageTitle
                text={translate('compliance.pages.modelcompliance.title')}
                secondaryText={translate(
                    'compliance.pages.modelcompliance.description'
                )}
                icon={<ComplianceIcon fontSize="large" />}
            />

            <ModelComplianceWidget entity={record} resource={resource as string} extension={field[0]}/>
        </ComplianceServicesProvider>
    );
};