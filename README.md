# cloud-itonami-isco-4225

**Community Inquiry Desk** — the ISCO-08 4225 (Inquiry Clerks) actor,
an ISCO **Wave 0 (cognitive substrate)** occupation per
ADR-2607121000: pure-cognitive work, the LLM-first wave, no robotics
gate.

**Maturity: `:implemented`** — InquiryAdvisor ⊣ InquiryClerksGovernor
as a langgraph StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt), modeled on
cloud-itonami-isco-4311's bookkeeping actor. 12 tests / 26 assertions
green.

The inquiry-specific HARD invariants: **an answer must cite a
REGISTERED knowledge-base entry** (an uncited answer is an invented
answer — the fleet's fabricated-spec-basis rule, information-desk
edition), and **the entry's validity window is checked
deterministically against the request date** — expired knowledge is
not servable at any confidence; the fix is refreshing the KB, not
approving harder. Also HARD: another client's KB entry, unregistered
organization, `:effect` other than `:propose`. Escalations (always
human sign-off): `:publish-faq` (external publication), low
confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
