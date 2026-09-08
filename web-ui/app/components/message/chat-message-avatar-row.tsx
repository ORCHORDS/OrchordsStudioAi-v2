import { useTranslation } from "react-i18next";

import { UIAvatar } from "~/components/ui/ui-avatar";
import { getAssistantDisplayName } from "~/lib/display";
import { useSettingsStore } from "~/stores";
import type { AssistantProfile, MessageDto, ProviderModel } from "~/types";

export interface ChatMessageAvatarRowProps {
  message: MessageDto;
  hasMessageContent: boolean;
  /** Kept for caller compatibility; ordinary model identity is intentionally not rendered. */
  loading: boolean;
  assistant?: AssistantProfile | null;
  /** Kept for caller compatibility; the conversation model is not repeated above every answer. */
  model?: ProviderModel | null;
}

function formatMessageTimestamp(createdAt: string, locale?: string): string | null {
  const timestamp = Date.parse(createdAt);
  if (Number.isNaN(timestamp)) return null;

  return new Intl.DateTimeFormat(locale || undefined, {
    dateStyle: "medium",
    timeStyle: "medium",
  }).format(timestamp);
}

export function ChatMessageAvatarRow({
  message,
  hasMessageContent,
  assistant,
}: ChatMessageAvatarRowProps) {
  const { t, i18n } = useTranslation(["common", "page"]);
  const displaySetting = useSettingsStore((state) => state.settings?.displaySetting);

  if (!hasMessageContent) {
    return null;
  }

  const createdAtLabel = formatMessageTimestamp(message.createdAt, i18n.language);

  if (message.role === "USER") {
    if (!displaySetting?.showUserAvatar) {
      return null;
    }

    const userName =
      displaySetting.userNickname.trim() ||
      t("page:conversations.user.default_name", { defaultValue: "User" });

    return (
      <div className="flex w-full justify-end px-1">
        <div className="flex items-center gap-2">
          <div className="min-w-0 text-right">
            <div className="truncate text-sm font-medium text-foreground/90">{userName}</div>
            {createdAtLabel ? (
              <div className="truncate text-xs text-muted-foreground/80">{createdAtLabel}</div>
            ) : null}
          </div>
          <UIAvatar name={userName} avatar={displaySetting.userAvatar} className="size-9" />
        </div>
      </div>
    );
  }

  if (message.role !== "ASSISTANT") {
    return null;
  }

  const useAssistantAvatar = assistant?.useAssistantAvatar === true;
  if (!useAssistantAvatar) {
    return null;
  }

  const showAssistantIcon = displaySetting?.showModelIcon !== false;
  const showAssistantName = displaySetting?.showModelName === true;
  if (!showAssistantIcon && !showAssistantName) {
    return null;
  }

  const assistantName = getAssistantDisplayName(assistant?.name);

  return (
    <div className="flex w-full justify-start px-1">
      <div className="flex min-w-0 items-center gap-2">
        {showAssistantIcon ? (
          <UIAvatar
            name={assistantName}
            avatar={assistant?.avatar}
            brand={assistant?.name?.trim() ? undefined : "default-assistant"}
            className="size-9"
          />
        ) : null}
        {showAssistantName ? (
          <div className="min-w-0">
            <div className="truncate text-sm font-medium text-foreground/90">{assistantName}</div>
            {createdAtLabel ? (
              <div className="truncate text-xs text-muted-foreground/80">{createdAtLabel}</div>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}
