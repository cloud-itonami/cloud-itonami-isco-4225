# cloud-itonami-isco-4225

**Community Inquiry Desk** — the ISCO-08 4225 (Inquiry Clerks) actor,
an ISCO **Wave 0 (cognitive substrate)** occupation per
ADR-2607121000: pure-cognitive work, the LLM-first wave, no robotics
gate.

**Maturity: `:implemented`** — InquiryAdvisor ⊣ InquiryClerksGovernor
as a langgraph StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt), modeled on
cloud-itonami-isco-4311's bookkeeping actor. 55 tests / 182 assertions
green.

## What it refuses

The desk's premise is that it **reads out registered information**. It
does not invent, does not serve knowledge past its expiry, and does not
adjudicate. Four HARD groups, none overridable at any confidence:

| group | rules |
|---|---|
| **vocabulary** | `:unsupported-op` (an op nobody declared) and `:reserved-op` (an act that is real desk-adjacent work but belongs to someone qualified) |
| **well-formedness** | `:incomplete-proposal`, `:ill-formed-field` — the fields an op's checks need, and a stated confidence in 0..1 |
| **provenance** | `:no-client`, `:no-actuation` (`:effect` must be `:propose`) |
| **knowledge basis** | `:no-kb-citation`, `:unknown-kb-entry`, `:kb-wrong-client`, `:stale-knowledge`, and — because freshness needs two dates — `:undatable-request`, `:unservable-kb-entry` |

Escalations (human sign-off, never silent): `:publish-faq` (external
publication) and confidence below `confidence-floor` (0.6).

`:unsupported-op` and `:reserved-op` are deliberately different names.
An enquirer told the first goes looking for a typo; one told the second
goes looking for the person allowed to do it. Collapsing them sends
half of them to the wrong place.

## The ledger says what authorised each write

Every commit and every hold records its `:authorisation` —
`:governor-clear`, `:human-sign-off`, or `:governor-hold` — and
`inquiry.ledger/entry` refuses to build an entry without one. This is
the question the trail is kept for: *did a human sign this off?*

## Refusals this actor could not make, measured on 0abfc59

Each of these was observed, not inferred, and each is now pinned by a
registered mutation in the superproject's `scripts/maturity-loop/`:

- `:purge-knowledge-base` and `:delete-client-records` returned
  `{:ok? true :violations []}` and the actor **committed a record** for
  them. The KB invariants keyed on `(= :answer-inquiry op)` and the
  escalation on `:publish-faq`, so every other op fell through both. An
  open vocabulary is not a permissive policy, it is the absence of one.
- `{:op :answer_inquiry :kb-id nil}` — a one-character typo — was
  approved with no citation at all. The central HARD rule was enforced
  only on proposals that spelled the op exactly right, so misspelling
  it **skipped** the rule rather than tripping it.
- `:today nil` (and the string `"20260713"`) served an entry that
  expired on 2023-03-31, because the comparison was guarded with
  `(integer? today)`. Omitting the date was a way to be served. The
  same held for an entry whose `:valid-until` was absent or a string.
- `:confidence 2.5` cleared the 0.6 floor and was committed; the string
  `"0.9"` threw `ClassCastException` out of `check`.
- `:op nil` threw `NullPointerException` in `advisor/infer`, which runs
  **before** the governor — so there was no verdict, no violation and
  no ledger entry. A crash is not a refusal.
- An automatic commit and one resumed through `actor/approve!` wrote
  entries with identical keys `(:disposition :record)`. The README
  promised that `:publish-faq` always requires sign-off, and the ledger
  could not show that any given write had received it.

## Layout

    src/inquiry/operation.cljk  the op vocabulary, once, as an allowlist
    src/inquiry/governor.cljk   the independent refusal layer
    src/inquiry/ledger.cljk     audit entries that name their authorisation
    src/inquiry/store.cljk      clients, KB entries, records, ledger (SSoT)
    src/inquiry/advisor.cljk    proposes only; mock and LLM
    src/inquiry/actor.cljk      the StateGraph wiring the above together

    clojure -M:test    55 tests / 182 assertions
    clojure -M:lint    0 errors

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
