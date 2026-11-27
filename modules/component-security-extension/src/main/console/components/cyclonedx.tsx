import { ReportError, SecurityReport } from './securityReport';
import {
    isObject,
    JsonObject,
    objectArray,
    SbomComponent,
    SbomDocument,
    SbomReport,
    stringValue,
} from './sbom';

const parseCycloneDx = (value: unknown): SbomDocument => {
    if (!isObject(value) || value.bomFormat !== 'CycloneDX') {
        throw new ReportError('security.report.invalidCycloneDx');
    }

    const components: SbomComponent[] = [];
    const componentNames = new Map<string, string>();
    const addComponent = (component: JsonObject) => {
        const name = [stringValue(component.group), stringValue(component.name)]
            .filter(Boolean)
            .join('/');
        const version = stringValue(component.version);
        const reference = stringValue(component['bom-ref']);
        if (reference) {
            componentNames.set(
                reference,
                [name, version].filter(Boolean).join('@')
            );
        }
        const licenses = objectArray(component.licenses, 'licenses')
            .map(entry => {
                if (isObject(entry.license)) {
                    return (
                        stringValue(entry.license.id) ||
                        stringValue(entry.license.name)
                    );
                }
                return stringValue(entry.expression);
            })
            .filter(Boolean)
            .join(', ');
        components.push({
            id: String(components.length),
            name,
            version,
            license: licenses,
            purl: stringValue(component.purl),
        });
        objectArray(component.components, 'components').forEach(addComponent);
    };

    const metadata = isObject(value.metadata) ? value.metadata : undefined;
    if (metadata && isObject(metadata.component))
        addComponent(metadata.component);
    objectArray(value.components, 'components').forEach(addComponent);

    const vulnerabilities =
        value.vulnerabilities === undefined
            ? undefined
            : objectArray(value.vulnerabilities, 'vulnerabilities').map(
                  (item, index) => {
                      const affects = objectArray(item.affects, 'affects');
                      const ratings = objectArray(item.ratings, 'ratings');
                      const analysis = isObject(item.analysis)
                          ? item.analysis
                          : undefined;
                      return {
                          id: String(index),
                          vulnerability: stringValue(item.id),
                          component: affects
                              .map(affected => {
                                  const ref = stringValue(affected.ref);
                                  return componentNames.get(ref) || ref;
                              })
                              .filter(Boolean)
                              .join(', '),
                          severity: ratings
                              .map(rating => stringValue(rating.severity))
                              .filter(Boolean)
                              .join(', '),
                          status: stringValue(analysis?.state),
                          description: stringValue(item.description),
                      };
                  }
              );

    const rootComponent =
        metadata && isObject(metadata.component)
            ? metadata.component
            : undefined;
    return {
        format: 'CycloneDX',
        version: stringValue(value.specVersion),
        name: stringValue(rootComponent?.name),
        components,
        vulnerabilities,
    };
};

export const CycloneDxReport = ({ report }: { report: SecurityReport }) => (
    <SbomReport report={report} format="CycloneDX" parse={parseCycloneDx} />
);
