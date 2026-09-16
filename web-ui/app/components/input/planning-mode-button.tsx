import * as React from "react";

import { useMutation } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";

import { PLANNING_MODE_ID, setPlanningModeEnabled } from "~/components/input/planning-mode-state";
import { Button } from "~/components/ui/button";
import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { safeStringArray } from "~/lib/type-guards";
import { cn } from "~/lib/utils";
import api from "~/services/api";
import { useChatInputStore } from "~/stores";
import type { ConversationDto } from "~/types";

const EMPTY_ID_LIST: string[] = [];

export interface PlanningModeButtonProps {
  disabled?: boolean;
  className?: string;
  conversation?: ConversationDto | null;
  draftKey?: string | null;
}

export function PlanningModeButton({
  disabled = false,
  className,
  conversation = null,
  draftKey = null,
}: PlanningModeButtonProps) {
  const { settings } = useCurrentAssistant();
  const draftModeInjectionIds = useChatInputStore(
    React.useCallback(
      (state) =>
        draftKey ? (state.drafts[draftKey]?.modeInjectionIds ?? EMPTY_ID_LIST) : EMPTY_ID_LIST,
      [draftKey],
    ),
  );
  const draftLorebookIds = useChatInputStore(
    React.useCallback(
      (state) =>
        draftKey ? (state.drafts[draftKey]?.lorebookIds ?? EMPTY_ID_LIST) : EMPTY_ID_LIST,
      [draftKey],
    ),
  );
  const setDraftPromptInjectionIds = useChatInputStore((state) => state.setPromptInjectionIds);

  const planningAvailable =
    settings?.modeInjections?.some((item) => item.id === PLANNING_MODE_ID) === true;
  const modeInjectionIds = conversation
    ? safeStringArray(conversation.modeInjectionIds)
    : draftModeInjectionIds;
  const lorebookIds = conversation
    ? safeStringArray(conversation.lorebookIds)
    : draftLorebookIds;
  const enabled = modeInjectionIds.includes(PLANNING_MODE_ID);

  const updateConversation = useMutation({
    mutationFn: (nextModeInjectionIds: string[]) => {
      if (!conversation) throw new Error("Conversation is required");
      return api.post<ConversationDto>(`conversations/${conversation.id}/injections`, {
        modeInjectionIds: nextModeInjectionIds,
        lorebookIds,
      });
    },
  });

  const handleToggle = React.useCallback(() => {
    if (disabled || !planningAvailable || updateConversation.isPending) return;
    const nextModeInjectionIds = setPlanningModeEnabled(modeInjectionIds, !enabled);

    if (conversation) {
      updateConversation.mutate(nextModeInjectionIds);
      return;
    }

    if (draftKey) {
      setDraftPromptInjectionIds(draftKey, {
        modeInjectionIds: nextModeInjectionIds,
        lorebookIds,
      });
    }
  }, [
    conversation,
    disabled,
    draftKey,
    enabled,
    lorebookIds,
    modeInjectionIds,
    planningAvailable,
    setDraftPromptInjectionIds,
    updateConversation,
  ]);

  if (!planningAvailable || (!conversation && !draftKey)) return null;

  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      aria-pressed={enabled}
      disabled={disabled || updateConversation.isPending}
      className={cn(
        "h-8 rounded-full px-3 text-xs",
        enabled
          ? "bg-primary/10 text-primary hover:bg-primary/15 hover:text-primary"
          : "text-muted-foreground hover:text-foreground",
        className,
      )}
      onClick={handleToggle}
    >
      {updateConversation.isPending ? <LoaderCircle className="size-3.5 animate-spin" /> : null}
      Plan
    </Button>
  );
}
