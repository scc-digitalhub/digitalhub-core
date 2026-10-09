import { humanize, humanizeEnumValue } from "../../schema/resolver";

type Translate = (key: string, options?: Record<string, unknown>) => string;

/** Long-text-ish keys get a multiline textfield instead of a single-line one. */
const LONG_TEXT_KEYS = new Set([
  "description", "goal", "purpose", "audience", "rationale", "requirement_text", "summary",
  "content", "justification", "narrative",
]);

export function isLongTextKey(key: string): boolean {
  return LONG_TEXT_KEYS.has(key);
}

export function fieldLabel(key: string, translate: Translate): string {
  return translate(`compliance.fields.${key}`, { _: humanize(key) });
}

export function enumLabel(value: string, translate: Translate): string {
  return translate(`compliance.enumValues.${value}`, { _: humanizeEnumValue(value) });
}
