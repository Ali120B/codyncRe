import type { MetadataRoute } from "next";
import { SITE } from "./site";

export const dynamic = "force-static";

// AI answer engines are named explicitly so a blanket rule elsewhere can't shut them out.
const AI_CRAWLERS = ["GPTBot", "OAI-SearchBot", "ChatGPT-User", "ClaudeBot", "Claude-SearchBot", "PerplexityBot", "Google-Extended", "Applebot-Extended"];

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: "*", allow: "/" }, { userAgent: AI_CRAWLERS, allow: "/" }],
    sitemap: `${SITE}/sitemap.xml`,
  };
}
