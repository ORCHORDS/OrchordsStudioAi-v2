import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  formatAnsweredValue,
  isAskUserComplete,
  parseAskUserQuestions,
  serializeAskUserAnswers,
  toggleMultiSelection,
} from "./ask-user-state";

test("parses selection_type and defaults unknown values to text", () => {
  assert.deepEqual(
    parseAskUserQuestions({ questions: [
      { id: "a", question: "A?", options: ["x"], selection_type: "single" },
      { id: "b", question: "B?", options: ["y"], selection_type: "multi" },
      { id: "c", question: "C?" },
      { id: "d", question: "D?", selection_type: "future" },
    ] }),
    [
      { id: "a", question: "A?", options: ["x"], selectionType: "single" },
      { id: "b", question: "B?", options: ["y"], selectionType: "multi" },
      { id: "c", question: "C?", options: [], selectionType: "text" },
      { id: "d", question: "D?", options: [], selectionType: "text" },
    ],
  );
});

test("multi options toggle independently without clearing other selections", () => {
  let selected: string[] = [];
  selected = toggleMultiSelection(selected, "alpha");
  selected = toggleMultiSelection(selected, "beta");
  assert.deepEqual(selected, ["alpha", "beta"]);
  selected = toggleMultiSelection(selected, "alpha");
  assert.deepEqual(selected, ["beta"]);
});

test("serializes multi values with the existing Android string wire contract", () => {
  const questions = parseAskUserQuestions({ questions: [
    { id: "single", question: "One?", selection_type: "single" },
    { id: "multi", question: "Many?", selection_type: "multi" },
  ] });
  const payload = serializeAskUserAnswers(
    questions,
    { single: "chosen", multi: "custom, exact" },
    { multi: ["alpha", "beta"] },
  );
  assert.deepEqual(JSON.parse(payload), {
    answers: { single: "chosen", multi: "alpha, beta, custom, exact" },
  });
});

test("multi questions accept selected options or a custom answer", () => {
  const questions = parseAskUserQuestions({ questions: [
    { id: "text", question: "Text?" },
    { id: "single", question: "One?", selection_type: "single" },
    { id: "multi", question: "Many?", selection_type: "multi" },
  ] });
  assert.equal(isAskUserComplete(questions, { text: "yes", single: "one" }, { multi: [] }), false);
  assert.equal(isAskUserComplete(questions, { text: "yes", single: "one", multi: "custom" }, { multi: [] }), true);
  assert.equal(isAskUserComplete(questions, { text: "yes", single: "one" }, { multi: ["a", "b"] }), true);
});

test("restored answers render legacy strings and tolerate array-valued answers", () => {
  assert.equal(formatAnsweredValue("alpha, beta"), "alpha, beta");
  assert.equal(formatAnsweredValue(["alpha", "beta"]), "alpha, beta");
  assert.equal(formatAnsweredValue(["alpha", 3, "beta"]), "alpha, beta");
});

test("web ask_user renderer wires multi state into accessible option buttons and submission", () => {
  const source = readFileSync(new URL("./tool-part.tsx", import.meta.url), "utf8");
  assert.match(source, /isAskUserComplete\(questions, answers, multiAnswers\)/);
  assert.match(source, /serializeAskUserAnswers\(questions, answers, multiAnswers\)/);
  assert.match(source, /q\.selectionType === "multi"/);
  assert.match(source, /aria-pressed=\{selected\}/);
});
