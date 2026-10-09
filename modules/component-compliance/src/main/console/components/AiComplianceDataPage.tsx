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
import { PageTitle } from '../../common/components/layout/PageTitle';
import { ComplianceIcon } from './icon';
import { useGetSchemas } from '../../common/jsonSchema/schemaController';
 
export const AiComplianceDataPage = (props: {
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
    if (!field || field.length === 0 || isLoading || !schemas) {
        return <></>;
    }

    return (
        <ComplianceServicesProvider services={createServices(dataProvider, authProvider)}>
            <PageTitle
                text={translate('compliance.pages.datacompliance.title')}
                secondaryText={translate(
                    'compliance.pages.datacompliance.description'
                )}
                icon={<ComplianceIcon fontSize="large" />}
            />

            <DatasetComplianceWidget entity={record} resource={resource as string} extension={field[0]} />
        </ComplianceServicesProvider>
    );
};