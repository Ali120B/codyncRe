/** One `search` hit (host `store::model::SearchHit`): where a message lives and its matched text. */
export interface SearchHit {
  /** The message's entry id (the row's `data-mid`). */
  entryId: string
  /** The chat (bot or group) the message is in. */
  botId: string
  botName: string
  /** The thread it's in; null in the main chat. */
  threadId: string | null
  /** `user` or `agent`. */
  kind: string
  /** The bot that replied (a group member); null for the user's own messages. */
  author: string | null
  /** The message's text around its first match; `before`/`after` may carry a leading/trailing `…`. */
  snippet: { before: string; matched: string; after: string }
  at: number
}
