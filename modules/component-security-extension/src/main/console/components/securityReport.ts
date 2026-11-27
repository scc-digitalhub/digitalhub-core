import type { TranslateFunction } from 'react-admin';

export interface SecurityReport {
    id: string;
    key?: string;
    name?: string;
    kind: string;
    spec?: {
        analyzer?: string | null;
        path?: string | null;
        format?: string | null;
        report_name?: string | null;
    } | null;
    status?: any;
}

export class ReportError extends Error {
    constructor(
        key: string,
        public readonly options?: Record<string, string | number>
    ) {
        super(key);
    }
}

export const reportErrorMessage = (
    error: unknown,
    translate: TranslateFunction
): string =>
    error instanceof ReportError
        ? translate(error.message, error.options)
        : error instanceof Error
          ? error.message
          : String(error);
