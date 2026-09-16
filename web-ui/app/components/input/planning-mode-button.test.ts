import assert from "node:assert/strict";
import test from "node:test";

import {
  PLANNING_MODE_ID,
  setPlanningModeEnabled,
} from "./planning-mode-state";

test("planning mode id matches the Android stable id", () => {
  assert.equal(PLANNING_MODE_ID, "164b9a03-828e-434e-8aa9-82c0e019a7fb");
});

test("enabling Planning preserves unrelated mode ids and does not duplicate Planning", () => {
  const other = "11111111-1111-4111-8111-111111111111";
  assert.deepEqual(setPlanningModeEnabled([other], true), [other, PLANNING_MODE_ID]);
  assert.deepEqual(
    setPlanningModeEnabled([other, PLANNING_MODE_ID, PLANNING_MODE_ID], true),
    [other, PLANNING_MODE_ID],
  );
});

test("disabling Planning removes only the Planning id", () => {
  const first = "11111111-1111-4111-8111-111111111111";
  const second = "22222222-2222-4222-8222-222222222222";
  assert.deepEqual(
    setPlanningModeEnabled([first, PLANNING_MODE_ID, second], false),
    [first, second],
  );
});
