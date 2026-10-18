import type { Metadata } from "next";
import AgentLanding from "../components/agent-landing";
import { agentPages } from "../agents/agents";

const page = agentPages[0];

export const metadata: Metadata = { title: page.title, description: page.description, alternates: { canonical: "/claude-code-iphone" } };

export default function Page() {
  return <AgentLanding page={page} />;
}
