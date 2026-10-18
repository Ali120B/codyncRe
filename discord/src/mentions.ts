// Release notes for the #releases announcement: each `@login` whose GitHub account is linked becomes a Discord
// mention, so Auto Tag can ping the people it thanks. The links stay private: only notes signed with
// MENTIONS_SECRET are answered.

import { githubId } from "./guild.ts";
import { verify } from "./hmac.ts";
import type { Env } from "./index.ts";

// A GitHub login after a space, a line start or "(", so emails and paths don't match.
const LOGIN = /(?<=^|[\s(])@([A-Za-z0-9][A-Za-z0-9-]{0,38})(?![\w-])/gm;

export async function mentions(req: Request, env: Env): Promise<Response> {
  const text = await req.text();
  if (req.method !== "POST" || !(await verify(env.MENTIONS_SECRET, text, req.headers.get("X-Signature") ?? ""))) {
    return new Response("Bad signature", { status: 401 });
  }
  // GitHub logins ignore case.
  const logins = new Set(Array.from(text.matchAll(LOGIN), (m) => m[1].toLowerCase()));
  const ids = new Map<string, string>();
  await Promise.all([...logins].map(async (login) => {
    const github = await githubId(env, login);
    const discord = github && (await env.LINKS.get(`github:${github}`));
    if (discord) ids.set(login, discord);
  }));
  const linked = text.replace(LOGIN, (m, login: string) => {
    const id = ids.get(login.toLowerCase());
    return id ? `<@${id}>` : m;
  });
  return Response.json({ text: linked, users: [...new Set(ids.values())] });
}
