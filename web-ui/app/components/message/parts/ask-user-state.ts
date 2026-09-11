export type AskUserSelectionType = "text" | "single" | "multi";

export interface AskUserQuestion {
  id: string;
  question: string;
  options: string[];
  selectionType: AskUserSelectionType;
}

type StringAnswers = Record<string, string | undefined>;
type MultiAnswers = Record<string, readonly string[] | undefined>;

function getArrayField(data: unknown, key: string): unknown[] {
  if (!data || typeof data !== "object" || Array.isArray(data)) return [];
  const value = (data as Record<string, unknown>)[key];
  return Array.isArray(value) ? value : [];
}

export function parseAskUserQuestions(args: unknown): AskUserQuestion[] {
  return getArrayField(args, "questions")
    .map((question) => {
      if (!question || typeof question !== "object" || Array.isArray(question)) return null;
      const record = question as Record<string, unknown>;
      const id = typeof record.id === "string" ? record.id : "";
      const prompt = typeof record.question === "string" ? record.question : "";
      if (!id || !prompt) return null;
      const rawOptions = Array.isArray(record.options) ? record.options : [];
      const options = rawOptions.filter((option): option is string => typeof option === "string");
      const rawSelectionType = record.selection_type;
      const selectionType: AskUserSelectionType =
        rawSelectionType === "single" || rawSelectionType === "multi" ? rawSelectionType : "text";
      return { id, question: prompt, options, selectionType };
    })
    .filter((question): question is AskUserQuestion => question !== null);
}

export function toggleMultiSelection(current: readonly string[] | undefined, option: string): string[] {
  const selected = current ?? [];
  return selected.includes(option)
    ? selected.filter((value) => value !== option)
    : [...selected, option];
}

export function isAskUserComplete(
  questions: readonly AskUserQuestion[],
  answers: StringAnswers,
  multiAnswers: MultiAnswers,
): boolean {
  return questions.length > 0 && questions.every((question) => {
    const custom = answers[question.id];
    if (question.selectionType === "multi") {
      return (multiAnswers[question.id]?.length ?? 0) > 0 || Boolean(custom?.trim());
    }
    return Boolean(custom?.trim());
  });
}

export function serializeAskUserAnswers(
  questions: readonly AskUserQuestion[],
  answers: StringAnswers,
  multiAnswers: MultiAnswers,
): string {
  const payload = Object.fromEntries(questions.map((question) => {
    if (question.selectionType !== "multi") return [question.id, answers[question.id] ?? ""];
    const values = [...(multiAnswers[question.id] ?? [])];
    const custom = answers[question.id];
    if (custom?.trim() && !values.includes(custom)) values.push(custom);
    return [question.id, values.join(", ")];
  }));
  return JSON.stringify({ answers: payload });
}

export function formatAnsweredValue(value: unknown): string {
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === "string").join(", ");
  return "";
}
