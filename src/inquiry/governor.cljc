(ns inquiry.governor
  "InquiryClerksGovernor — the independent safety/traceability layer
  for the ISCO-08 4225 community inquiry-desk actor (itonami actor
  pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. The inquiry-specific
  twist: an ANSWER must cite a registered knowledge-base entry, and the
  entry's validity window is checked DETERMINISTICALLY against the
  request date — the desk never serves invented or stale knowledge, at
  any confidence.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the request's organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. KB citation basis — an :answer-inquiry must cite a REGISTERED
                           kb-entry belonging to this client (no
                           invented answers — the fabricated-spec-basis
                           rule, information-desk edition).
    4. freshness         — the cited entry's :valid-until must be >= the
                           request's :today. Serving expired knowledge
                           is not approvable; refresh the KB instead.
  ESCALATION invariants (:escalate? true, human sign-off):
    5. :op :publish-faq  (external publication).
    6. low confidence (< `confidence-floor`)."
  (:require [inquiry.store :as store]))

(def confidence-floor 0.6)
(def ^:private escalating-ops #{:publish-faq})

(defn- hard-violations [{:keys [request proposal]} client-record kb-record]
  (let [{:keys [op kb-id]} proposal
        answering? (= :answer-inquiry op)
        today (:today request)]
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

      (and answering? kb-record (integer? today)
           (integer? (:valid-until kb-record))
           (< (:valid-until kb-record) today))
      (conj {:rule :stale-knowledge
             :detail (str "KB entry の有効期限切れ: valid-until "
                          (:valid-until kb-record) " < today " today
                          "（期限切れ知識の案内は承認不可 — KB を更新せよ）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `inquiry.store/Store`. Pure — never mutates the
  store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        kb-record (some->> (:kb-id proposal) (store/kb-entry store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record kb-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
