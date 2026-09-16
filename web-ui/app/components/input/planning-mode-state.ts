export const PLANNING_MODE_ID = "164b9a03-828e-434e-8aa9-82c0e019a7fb";

export function setPlanningModeEnabled(ids: string[], enabled: boolean): string[] {
  const next = ids.filter((id, index) => id !== PLANNING_MODE_ID && ids.indexOf(id) === index);
  if (enabled) next.push(PLANNING_MODE_ID);
  return next;
}
