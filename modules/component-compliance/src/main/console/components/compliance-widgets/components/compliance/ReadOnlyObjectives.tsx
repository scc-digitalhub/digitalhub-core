import { Box, Chip, Stack, Typography } from "@mui/material";
import { useTranslate } from "react-admin";
import type { JsonRecord } from "../../types";
import { enumLabel } from "../schemaForm/fieldHelpers";

interface ReadOnlyObjectivesProps {
  objectives: JsonRecord[];
}

/** Non-editable summary of compliance objectives — objectives are owned/edited at the dataset
 * and model level, not directly on the project. */
export default function ReadOnlyObjectives({ objectives }: ReadOnlyObjectivesProps) {
  const t = useTranslate();

  if (objectives.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t("compliance.form.emptyArray")}
      </Typography>
    );
  }

  return (
    <Stack spacing={1}>
      {objectives.map((objective, index) => (
        <Box key={index} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 1, p: 1.5 }}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Typography variant="subtitle2">{(objective.name as string) || `#${index + 1}`}</Typography>
            {objective.priority ? <Chip size="small" label={enumLabel(objective.priority as string, t)} /> : null}
            {objective.severity ? <Chip size="small" label={enumLabel(objective.severity as string, t)} /> : null}
          </Stack>
          {objective.description ? (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {objective.description as string}
            </Typography>
          ) : null}
        </Box>
      ))}
    </Stack>
  );
}
