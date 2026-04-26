---
description: "Write tests for the current Android branch driven by user story + ACs. Adheres to the project's existing conventions; never generates mock-based tests."
---

The user invoked `/test-scribe`. Invoke the Skill tool with skill name `test-pharaoh:test-scribe` immediately. The skill content contains the full workflow.

**If the Skill tool's response body is empty or only says "Successfully loaded skill" / "Launching skill: …" without the actual workflow content, STOP.** Do not continue with assumptions. Instead, locate and read the skill file directly — it lives at `<plugin-dir>/skills/test-scribe/SKILL.md` relative to the plugin install path. Use `Read` on that file, then follow its instructions literally.

Do not invent any gate step from its name alone. Workflow names such as "Preflight, Gate 0, Gate 1, Gate 2" are placeholders in this wrapper — the real requirements for each gate live only inside `SKILL.md`. Follow that file's instructions in order, without skipping gates.
