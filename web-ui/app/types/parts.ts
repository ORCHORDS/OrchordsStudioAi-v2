/**
 * Tool approval state
 * @see ai/src/main/java/com/orchords/ai/ui/Message.kt - ToolApprovalState
 */
export type ToolApprovalState =
  | { type: "auto" }
  | { type: "pending" }
  | { type: "approved" }
  | { type: "denied"; reason: string }
  | { type: "answered"; answer: string };

interface BaseMessagePart {
  metadata?: Record<string, unknown> | null;
}

export interface TextPart extends BaseMessagePart {
  type: "text";
  text: string;
}

export interface ImagePart extends BaseMessagePart {
  type: "image";
  url: string;
}

export interface VideoPart extends BaseMessagePart {
  type: "video";
  url: string;
}

export interface AudioPart extends BaseMessagePart {
  type: "audio";
  url: string;
}

export interface DocumentPart extends BaseMessagePart {
  type: "document";
  url: string;
  fileName: string;
  mime: string;
}

export type McpResourceKind = "link" | "embedded_text" | "embedded_blob" | "embedded_unknown";

export interface McpResultStatusPart extends BaseMessagePart {
  type: "mcp_result_status";
  isError: boolean;
}

export interface McpStructuredPart extends BaseMessagePart {
  type: "mcp_structured";
  content: unknown;
}

export interface McpResourcePart extends BaseMessagePart {
  type: "mcp_resource";
  kind: McpResourceKind;
  uri: string;
  name?: string | null;
  title?: string | null;
  description?: string | null;
  mimeType?: string | null;
  size?: number | null;
  text?: string | null;
  localUrl?: string | null;
}

export interface ReasoningPart extends BaseMessagePart {
  type: "reasoning";
  reasoning: string;
  createdAt?: string;
  finishedAt?: string | null;
}

export interface ToolPart extends BaseMessagePart {
  type: "tool";
  toolCallId: string;
  toolName: string;
  input: string;
  output: UIMessagePart[];
  approvalState: ToolApprovalState;
}

/**
 * Union type for all message parts
 * @see ai/src/main/java/com/orchords/ai/ui/Message.kt - UIMessagePart
 */
export type UIMessagePart =
  | TextPart
  | ImagePart
  | VideoPart
  | AudioPart
  | DocumentPart
  | McpResultStatusPart
  | McpStructuredPart
  | McpResourcePart
  | ReasoningPart
  | ToolPart;
