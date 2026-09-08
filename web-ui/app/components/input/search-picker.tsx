import * as React from "react";

import { useMutation } from "@tanstack/react-query";
import { Earth, LoaderCircle } from "lucide-react";
import { useTranslation } from "react-i18next";

import { AIIcon } from "~/components/ui/ai-icon";
import { Button } from "~/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from "~/components/ui/popover";
import { Switch } from "~/components/ui/switch";
import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { usePickerPopover } from "~/hooks/use-picker-popover";
import { extractErrorMessage } from "~/lib/error";
import { cn } from "~/lib/utils";
import api from "~/services/api";

import { PickerErrorAlert } from "./picker-error-alert";

const ORCHORDS_SEARCH_LABEL = "Orchords Search";

export interface SearchPickerButtonProps {
  disabled?: boolean;
  className?: string;
}

/**
 * First-party external Web Search toggle.
 *
 * The old Bing/provider chooser and provider-native Search switch are not part
 * of the oai-1.0 contract. When enabled, chat exposes the local search_web
 * function tool and the configured Orchords Search service handles retrieval.
 */
export function SearchPickerButton({ disabled = false, className }: SearchPickerButtonProps) {
  const { t } = useTranslation("input");
  const { settings, currentAssistant } = useCurrentAssistant();
  const canUse = Boolean(settings && currentAssistant && !disabled);
  const { error, setError, popoverProps } = usePickerPopover(canUse);
  const searchEnabled = currentAssistant?.enableWebSearch ?? false;

  React.useEffect(() => {
    if (!canUse) {
      popoverProps.onOpenChange(false);
    }
  }, [canUse]);

  const toggleSearchEnabledMutation = useMutation({
    mutationFn: ({ enabled }: { enabled: boolean }) =>
      api.post<{ status: string }>("settings/search/enabled", {
        assistantId: currentAssistant?.id,
        enabled,
      }),
    onError: (toggleError) => {
      setError(extractErrorMessage(toggleError, t("search.update_search_failed")));
    },
    onSuccess: () => setError(null),
  });

  const loading = toggleSearchEnabledMutation.isPending;

  return (
    <Popover {...popoverProps}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={!canUse || loading}
          className={cn(
            "h-8 rounded-full px-2 text-muted-foreground hover:text-foreground",
            searchEnabled && "text-primary hover:bg-primary/10",
            className,
          )}
        >
          {loading ? (
            <LoaderCircle className="size-4 animate-spin" />
          ) : searchEnabled ? (
            <AIIcon
              name={ORCHORDS_SEARCH_LABEL}
              size={16}
              className="bg-transparent"
              imageClassName="h-full w-full"
            />
          ) : (
            <Earth className="size-4" />
          )}
        </Button>
      </PopoverTrigger>

      <PopoverContent align="end" className="w-[min(92vw,28rem)] gap-0 p-0">
        <PopoverHeader className="border-b px-6 py-4">
          <PopoverTitle>{t("search.title")}</PopoverTitle>
          <PopoverDescription>{t("search.description")}</PopoverDescription>
        </PopoverHeader>

        <div className="space-y-4 px-4 py-4">
          <PickerErrorAlert error={error} />

          <div className="flex items-center gap-3 rounded-lg border px-3 py-3">
            <AIIcon
              name={ORCHORDS_SEARCH_LABEL}
              size={32}
              className="shrink-0 bg-transparent"
              imageClassName="h-full w-full"
            />
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium">{ORCHORDS_SEARCH_LABEL}</div>
              <div className="text-muted-foreground text-xs">
                {searchEnabled ? t("search.status_enabled") : t("search.status_disabled")}
              </div>
            </div>
            <Switch
              checked={searchEnabled}
              disabled={disabled || loading}
              onCheckedChange={(nextChecked) => {
                if (!canUse) return;
                toggleSearchEnabledMutation.mutate({ enabled: nextChecked });
              }}
            />
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
