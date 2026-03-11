export interface EmailPart {
  id: number;
  partIndex: number;
  contentType: string;
  charset: string | null;
  transferEncoding: string | null;
  content: string;
}

export interface EmailAttachment {
  id: number;
  fileName: string | null;
  contentType: string;
  charset: string | null;
  transferEncoding: string | null;
  sizeBytes: number;
}

export interface Email {
  id: number;
  from: string;
  to: string;
  subject: string;
  body: string;
  receivedAt: string;
  parts?: EmailPart[];
  attachments?: EmailAttachment[];
}
