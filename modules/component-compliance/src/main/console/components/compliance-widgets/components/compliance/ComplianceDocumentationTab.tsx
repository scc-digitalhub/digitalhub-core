import { useTranslate } from "react-admin";
import { Box, Stack, Typography } from "@mui/material";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { JsonRecord } from "../../types";
import SchemaForm from "../schemaForm/SchemaForm";
import SectionAccordion from "../common/SectionAccordion";
import { useSchemaReadOnly } from "../schemaForm/SchemaReadOnlyContext";

interface ComplianceDocumentationTabProps {
  documentation: JsonRecord;
  setDocumentation: (next: JsonRecord) => void;
}

interface DocumentationSection extends JsonRecord {
  title?: string;
  content?: string;
  specification?: unknown;
  subsections?: DocumentationSection[];
}

function DocumentationSectionView({ section, depth = 0 }: { section: DocumentationSection; depth?: number }) {
  const specification = section.specification;
  const hasSpecification = specification !== undefined && specification !== null
    && (typeof specification !== "object" || Object.keys(specification as object).length > 0);

  return (
    <Box component="section">
      <Typography variant={depth === 0 ? "h5" : "h6"} component={depth === 0 ? "h2" : "h3"} gutterBottom>
        {section.title}
      </Typography>
      {hasSpecification && (
        <SectionAccordion title="Formal specification" defaultExpanded={false}>
          <Box component="pre" sx={{ m: 0, overflowX: "auto", whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
            {typeof specification === "string" ? specification : JSON.stringify(specification, null, 2)}
          </Box>
        </SectionAccordion>
      )}
      {section.content && (
        <Box sx={{ mt: hasSpecification ? 2 : 0, "& > :first-of-type": { mt: 0 }, "& > :last-child": { mb: 0 } }}>
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{section.content}</ReactMarkdown>
        </Box>
      )}
      {section.subsections && section.subsections.length > 0 && (
        <Stack spacing={3} sx={{ mt: 3, pl: { xs: 1, sm: 2 }, borderLeft: 2, borderColor: "divider" }}>
          {section.subsections.map((subsection, index) => (
            <DocumentationSectionView key={`${subsection.title ?? "section"}-${index}`} section={subsection} depth={depth + 1} />
          ))}
        </Stack>
      )}
    </Box>
  );
}

/** Shared "Documentation" tab content (ComplianceDocumentation), identical across all specs. */
export default function ComplianceDocumentationTab({ documentation, setDocumentation }: ComplianceDocumentationTabProps) {
  const t = useTranslate();
  const readOnly = useSchemaReadOnly();
  const sections = Array.isArray(documentation.sections) ? documentation.sections as DocumentationSection[] : [];

  if (readOnly) {
    return (
      <Stack spacing={4}>
        {sections.map((section, index) => (
          <DocumentationSectionView key={`${section.title ?? "section"}-${index}`} section={section} />
        ))}
      </Stack>
    );
  }

  return (
    <SectionAccordion title={t("compliance.fields.sections")}>
      <SchemaForm schemaName="ComplianceDocumentation" only={["sections"]} value={documentation} onChange={setDocumentation} />
    </SectionAccordion>
  );
}
