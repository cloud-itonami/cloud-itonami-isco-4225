(ns inquiry.governor
  "InquiryClerksGovernor — the independent safety/traceability layer
  for the ISCO-08 4225 community inquiry-desk actor (itonami actor
  pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. The inquiry-specific
  twist: an ANSWER must cite a registered knowledge-base entry, and the
  entry's validity window is checked DETERMINISTICALLY against the
  request date — the desk never serves invented or stale knowledge, at
  any confidence.

  Vocabulary and well-formedness (`inquiry.operation`) run BEFORE the
  KB invariants. Measured on 0abfc59, when they did not: the four KB
  rules keyed on `(= :answer-inquiry op)` and the freshness rule on
  `(integer? today)`, so an undeclared op, a misspelled op, and a
  request that simply omitted its date all fell through every check and
  came back `{:ok? true :violations []}`. A rule reachable only by
  proposals that already volunteered the right shape is not enforced;
  it is offered.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. vocabulary        — the op must be one this actor may propose
                           (:unsupported-op), and must not be one
                           reserved to a qualified human (:reserved-op).
    2. well-formedness   — the proposal must carry the fields its op
                           needs, and a stated confidence must be 0..1.
    3. client provenance — the request's organization must be registered.
    4. no-actuation      — proposal :effect must be :propose.
    5. KB citation basis — an :answer-inquiry must cite a REGISTERED
                           kb-entry belonging to this client (no
                           invented answers — the fabricated-spec-basis
                           rule, information-desk edition).
    6. freshness         — the cited entry's :valid-until must be >= the
                           request's :today, and BOTH must be stateable
                           as integers (:undatable-request /
                           :unservable-kb-entry). Serving expired — or
                           undatable — knowledge is not approvable;
                           refresh the KB instead.
  ESCALATION invariants (:escalate? true, human sign-off):
    7. an op declaring :escalates? (:publish-faq — external publication).
    8. low confidence (< `confidence-floor`)."
  (:require [inquiry.operation :as operation]
            [inquiry.store :as store]))

(def confidence-floor 0.6)

(defn- vocabulary-violations [op]
  (let [{:keys [status reason]} (operation/classify op)]
    (case status
      :supported   []
      :reserved    [{:rule :reserved-op :detail reason}]
      :unsupported [{:rule :unsupported-op
                     :detail (str "この actor が提案できる op ではない（受領: "
                                  (pr-str op) "、既知: "
                                  (pr-str (sort (keys operation/supported))) "）")}])))

(defn- invariant-violations [{:keys [request proposal]} client-record kb-record]
  (let [{:keys [op kb-id]} proposal
        answering? (= :answer-inquiry op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and answering? (nil? kb-id))
      (conj {:rule :no-kb-citation :detail "回答はナレッジベース引用が必須（回答の捏造禁止）"})

      (and answering? kb-id (nil? kb-record))
      (conj {:rule :unknown-kb-entry :detail (str "未登録の KB entry: " kb-id)})

      (and answering? kb-record
           (not= (:client-id kb-record) (:client-id request)))
      (conj {:rule :kb-wrong-client :detail "KB entry が別 client のもの"})

      ;; Freshness needs two dates. Neither is optional: a missing one
      ;; used to skip the comparison, which is how a 2023 entry was
      ;; served in 2026.
      (and answering? kb-record (operation/undatable-fault request))
      (conj {:rule :undatable-request :detail (operation/undatable-fault request)})

      (and answering? kb-record (operation/unservable-fault kb-record))
      (conj {:rule :unservable-kb-entry :detail (operation/unservable-fault kb-record)})

      (and answering? kb-record
           (integer? (:today request))
           (integer? (:valid-until kb-record))
           (< (:valid-until kb-record) (:today request)))
      (conj {:rule :stale-knowledge
             :detail (str "KB entry の有効期限切れ: valid-until "
                          (:valid-until kb-record) " < today " (:today request)
                          "（期限切れ知識の案内は承認不可 — KB を更新せよ）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `inquiry.store/Store`. Pure — never mutates the
  store. Total: every proposal, including one whose op is nil or whose
  confidence is a string, yields a verdict rather than an exception. A
  crash is not a refusal — it produces no verdict, no violation and no
  ledger entry, so the one thing the actor exists to do does not
  happen."
  [request context proposal store]
  (let [op (:op proposal)
        vocab (vocabulary-violations op)
        supported? (empty? vocab)
        shape (if supported? (operation/malformed proposal) [])
        client-record (store/client store (:client-id request))
        kb-record (some->> (:kb-id proposal) (store/kb-entry store))
        hard (vec (concat vocab shape
                          (invariant-violations {:request request :proposal proposal}
                                                client-record kb-record)))
        hard? (boolean (seq hard))
        conf (:confidence proposal)
        ;; An absent confidence is the lowest confidence, as it always
        ;; was. A present non-numeric one is already HARD above, and is
        ;; not compared here — that comparison is what used to throw.
        low? (or (nil? conf) (and (number? conf) (< conf confidence-floor)))
        risky-op? (operation/escalates? op)]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence (if (number? conf) conf 0.0)
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
