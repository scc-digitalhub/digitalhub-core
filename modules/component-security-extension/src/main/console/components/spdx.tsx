import { ReportError, SecurityReport } from './securityReport';
import {
    isObject,
    objectArray,
    SbomDocument,
    SbomReport,
    stringValue,
} from './sbom';

const parseSpdx = (value: unknown): SbomDocument => {
    if (!isObject(value)) {
        throw new ReportError('security.report.invalidSpdx');
    }

    const version = stringValue(value.spdxVersion);
    if (version.startsWith('SPDX-2.')) {
        const packages = objectArray(value.packages, 'packages');
        return {
            format: 'SPDX',
            version,
            name: stringValue(value.name),
            components: packages.map((pkg, index) => {
                const references = objectArray(
                    pkg.externalRefs,
                    'externalRefs'
                );
                const purl = references.find(reference =>
                    stringValue(reference.referenceType)
                        .toLowerCase()
                        .endsWith('purl')
                );
                return {
                    id: String(index),
                    name: stringValue(pkg.name),
                    version: stringValue(pkg.versionInfo),
                    license:
                        stringValue(pkg.licenseConcluded) ||
                        stringValue(pkg.licenseDeclared),
                    purl: stringValue(purl?.referenceLocator),
                };
            }),
        };
    }

    if (value['@graph'] !== undefined || value.type === 'SpdxDocument') {
        const graph =
            value['@graph'] === undefined
                ? [
                      value,
                      ...(Array.isArray(value.element)
                          ? value.element.filter(isObject)
                          : []),
                  ]
                : objectArray(value['@graph'], '@graph');
        const document = graph.find(item => item.type === 'SpdxDocument');
        const creationInfo = isObject(document?.creationInfo)
            ? document.creationInfo
            : graph.find(
                  item =>
                      item.type === 'CreationInfo' &&
                      (!document?.creationInfo ||
                          item['@id'] === document.creationInfo)
              );
        if (
            !document ||
            !creationInfo ||
            !stringValue(creationInfo.specVersion).startsWith('3.')
        ) {
            throw new ReportError('security.report.invalidSpdxJsonLd');
        }
        return {
            format: 'SPDX',
            version: stringValue(creationInfo.specVersion),
            name: stringValue(document.name),
            components: graph
                .filter(item => item.type === 'software_Package')
                .map((pkg, index) => ({
                    id: String(index),
                    name: stringValue(pkg.name),
                    version: stringValue(pkg.software_packageVersion),
                    license: '',
                    purl: stringValue(pkg.software_packageUrl),
                })),
        };
    }

    throw new ReportError('security.report.unsupportedSpdx');
};

export const SpdxReport = ({ report }: { report: SecurityReport }) => (
    <SbomReport report={report} format="SPDX" parse={parseSpdx} />
);
