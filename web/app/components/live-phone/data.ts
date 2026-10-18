// Colors from CodyncKit/Design/Theme.swift (light appearance) and the demo story the
// App Store screenshots tell: a `pace` running app, an ISO week bug in src/pace.js.

export const palette = {
  background: "#FFFFFF",
  surface: "#F4F4F4",
  bubbleAgent: "#F0F0F0",
  bubbleUser: "#E2E2E2",
  border: "#E6E6E6",
  text: "#141414",
  secondary: "#6B6B6B",
  tertiary: "#9B9B9B",
  accentFill: "#000000",
  accentDim: "#D9D9D9",
  danger: "#C23A2B",
  warning: "#F0A030",
  codeBackground: "#F4F4F4",
  added: "#2E7D32",
  removed: "#C62828",
};

const swatches: Record<string, string> = {
  black: "#2B2B2B", brown: "#936439", red: "#FF263C", orange: "#FF6700", yellow: "#FF9800", green: "#00C972",
  cyan: "#00BCA6", blue: "#1084FE", violet: "#9159FE", magenta: "#FF309B", gray: "#777777",
};
export const swatch = (id: string) => swatches[id] ?? swatches.blue;

export type Permission = {
  title: string;
  /** null while it waits for an answer. */
  outcome: string | null;
  answering: string | null;
};

export type Entry = { id: string } & (
  | { kind: "separator"; text: string }
  | { kind: "user"; text: string; queued?: boolean }
  | { kind: "agent"; text: string; author?: string }
  | { kind: "permission"; permission: Permission }
);

export type Chat = {
  id: string;
  name: string;
  shape: string;
  color: string;
  members?: string[];
  unread: number;
  /** Minutes after midnight of the last message. */
  at: number;
  entries: Entry[];
  /** The live activity line (WorkingIndicator), null when idle. */
  activity: { text: string; needs: boolean } | null;
};

export const clock = (m: number) => `${Math.floor(m / 60) % 12 || 12}:${String(m % 60).padStart(2, "0")} ${m < 720 ? "AM" : "PM"}`;

const sep = (id: string, m: number): Entry => ({ id, kind: "separator", text: `Today ${clock(m)}` });

export const initialChats: Chat[] = [
  {
    id: "pacer", name: "Pacer", shape: "hex", color: "orange", unread: 1, at: 78,
    activity: { text: "Needs approval: Edit src/pace.js", needs: true },
    entries: [
      sep("p0", 78),
      { id: "p1", kind: "user", text: "Reviewer says late-evening runs can land in the wrong week. Can you fix it?" },
      { id: "p2", kind: "permission", permission: { title: "Edit src/pace.js", outcome: null, answering: null } },
    ],
  },
  {
    id: "ship", name: "Ship room", shape: "blob", color: "gray", members: ["reviewer", "scout"], unread: 2, at: 73, activity: null,
    entries: [
      sep("s0", 72),
      { id: "s1", kind: "user", text: "Is weeklyTotals ready to ship? One short sentence each." },
      {
        id: "s2", kind: "agent", author: "reviewer",
        text: 'Almost: the week math is right and all 4 tests pass, but a bad date gets bucketed as `"NaN-WNaN"` instead of raising an error, and a late-evening run with a timezone offset can count toward the next week because grouping is in UTC. @Scout, can you add an invalid-date guard and confirm whether weeks should be UTC or the runner\'s local time?',
      },
      {
        id: "s3", kind: "agent", author: "scout",
        text: "I added the guard: `weeklyTotals` now throws a `RangeError` on an invalid date, and all 5 tests pass (4 old plus a new one for bad dates). Weeks are still grouped in UTC, which I chose myself, so the user needs to say whether that's right or weeks should follow the runner's local time. Nothing is committed yet.",
      },
    ],
  },
  {
    id: "relay", name: "Relay", shape: "squircle", color: "blue", unread: 1, at: 72, activity: null,
    entries: [
      sep("r0", 70),
      { id: "r1", kind: "user", text: "Show each runner's weekly totals in the group run digest." },
      {
        id: "r2", kind: "agent",
        text: "Added weeklyTotals to group run distances in the digest, so each runner sees their km for the current ISO week. The digest test passes and nothing is committed yet.",
      },
    ],
  },
  {
    id: "reviewer", name: "Reviewer", shape: "tablet", color: "violet", unread: 1, at: 67, activity: null,
    entries: [
      sep("v0", 66),
      { id: "v1", kind: "user", text: "Review weeklyTotals in src/pace.js before we ship." },
      {
        id: "v2", kind: "agent",
        text: "I found no blockers. The ISO week math matches the spec and the year-boundary test covers `2026-W53`. Two notes for later: bad dates aren't rejected yet, and weeks are grouped in UTC.",
      },
    ],
  },
  {
    id: "scout", name: "Scout", shape: "cloud", color: "green", unread: 1, at: 64, activity: null,
    entries: [
      sep("c0", 63),
      { id: "c1", kind: "user", text: "Add a weeklyTotals(runs) helper that sums distance and time per ISO week, with a test. Reply in two short sentences." },
      {
        id: "c2", kind: "agent",
        text: 'I added `weeklyTotals(runs)` to `src/pace.js`: it takes runs shaped like `{ date, distance, time }`, groups them by ISO week in UTC (keys like `"2026-W53"`), and I added a test that checks the week that crosses into 2027. All 4 tests pass and nothing is committed yet.',
      },
    ],
  },
];

/** The diff behind Pacer's approval card ("Details"). */
export const paceDiff = [
  "@@ function isoWeek(run) @@",
  "-  const d = new Date(run.date)",
  "-  const day = d.getUTCDay() || 7",
  "+  const d = localDate(run.date, run.offset)",
  "+  const day = d.getDay() || 7",
  "   d.setDate(d.getDate() + 4 - day)",
];

export const approvalReply: Record<string, string> = {
  "Allowed once": "Fixed: `isoWeek` now reads the runner's local date, so a Sunday 11 PM run stays in its own week. All 6 tests pass and nothing is committed yet.",
  "Always allowed": "Fixed: `isoWeek` now reads the runner's local date, so a Sunday 11 PM run stays in its own week. All 6 tests pass and nothing is committed yet.",
  Denied: "Okay, I left `src/pace.js` as it is. Tell me if you want the fix done another way.",
};

/** Short scripted answers to whatever the visitor types, taken in turn. */
const scripts: Record<string, string[]> = {
  pacer: [
    "On it. I'll keep the change inside `src/pace.js` and run the tests before I reply.",
    "Done. The tests still pass and nothing is committed yet.",
  ],
  relay: [
    "The digest now shows this week's km per runner. Want last week next to it for comparison?",
    "Done, and the digest test still passes.",
  ],
  reviewer: [
    "Looks good to me. The one case I'd still test is a run logged exactly at midnight.",
    "No blockers from my side.",
  ],
  scout: [
    "Added it to `src/pace.js` with a test. All tests pass and nothing is committed yet.",
    "Checked: `weeklyTotals` handles an empty list and returns `{}`.",
  ],
  reviewer_group: ["From my side it ships once weeks follow the runner's local time."],
  scout_group: ["Will do: I'll group weeks in the runner's local time and add a test for a Sunday 11 PM run."],
};

export function scriptedReply(chat: Chat, text: string, turn: number): { text: string; author?: string } {
  if (chat.members) {
    const author = /@scout/i.test(text) ? "scout" : "reviewer";
    const lines = scripts[`${author}_group`];
    return { text: lines[turn % lines.length], author };
  }
  const lines = scripts[chat.id] ?? scripts.scout;
  return { text: lines[turn % lines.length] };
}
