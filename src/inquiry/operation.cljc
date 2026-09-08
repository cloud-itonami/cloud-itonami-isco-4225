(ns inquiry.operation
  "The op vocabulary of the ISCO-08 4225 community inquiry-desk actor,
  stated once, as an ALLOWLIST.

  Measured on 0abfc59, before this namespace existed. The governor
  applied its four KB invariants only when `(= :answer-inquiry op)` and
  its escalation only when `(= :publish-faq op)`, so every other op
  fell through both and came back `{:ok? true :violations []}`:

    :purge-knowledge-base   -> ok? true, and the full actor COMMITTED
    :delete-client-records  -> ok? true, and the full actor COMMITTED
    \"answer-inquiry\" (str)  -> ok? true, and the full actor COMMITTED

  An open vocabulary is not a permissive policy, it is the absence of
  one. Worse, because the KB checks keyed on the op SPELLING, a typo
  was a way to be approved without citing anything:

    {:op :answer_inquiry :kb-id nil} -> {:ok? true :violations []}

  The README's central HARD rule is that an answer must cite a
  registered KB entry. It was enforced only on proposals that spelled
  the op exactly right, and misspelling it skipped the rule rather than
  tripping it.

  Two maps, not one, because the two refusals are not the same refusal:

    `supported`  what this actor may propose. An op outside it is a
                 VOCABULARY error — someone misspelled an op, or asked
                 for something this desk was never built to do.
    `reserved`   acts that are real work at a public-facing desk but are
                 NOT this actor's to propose. An op inside it is an
                 AUTHORITY boundary: ISCO-08 4225 is an INQUIRY clerk —
                 it reads out registered information. Adjudicating,
                 advising professionally, amending the authoritative
                 record and disclosing personal data are other people's
                 acts, and stay theirs.

  An enquirer told `unsupported-op` goes looking for a typo. One told
  `reserved-op` goes looking for the person who is allowed to do it.
  Collapsing them sends half of them to the wrong place.

  A supported op also declares the fields a proposal MUST carry
  (`:requires`), checked BEFORE the invariants so that a missing field
  is refused rather than skipped."
  (:require [kotoba.lang.text :as str]))

(def supported
  "op -> {:summary :requires :escalates?}. `:requires` are the proposal
  fields without which the desk cannot perform the operation at all.

  `:kb-id` is deliberately NOT in `:answer-inquiry`'s `:requires`: an
  uncited answer is refused by the named HARD rule `:no-kb-citation`,
  which says why it is forbidden. Demoting it to a shape error would
  relabel the actor's central invariant as a missing field."
  {:answer-inquiry
   {:summary    "Answer an inquiry from a registered, in-date knowledge-base entry."
    :requires   #{:answer}
    :escalates? false}

   :log-inquiry
   {:summary    "Record an inquiry that was received. No knowledge is served."
    :requires   #{}
    :escalates? false}

   :publish-faq
   {:summary    "Publish an FAQ externally. Always human sign-off."
    :requires   #{}
    :escalates? true}})

(def reserved
  "op -> why it is not this actor's to propose. Refused with an authority
  reason, never admitted and never merely escalated: an escalation asks
  a human to approve THIS actor's proposal, and these are acts the desk
  may not put in front of a human as its own."
  {:give-legal-advice
   "法的助言は有資格者の職務であり、登録済み情報の案内とは別の行為（案内窓口の範囲外）"
   :give-medical-advice
   "医学的助言は有資格者の職務であり、案内窓口の範囲外"
   :decide-application
   "申請の可否判定は決裁権限であって、案内窓口の行為ではない"
   :amend-official-record
   "公的記録の訂正は記録管理者の行為（窓口は記録を読み上げるが書き換えない）"
   :release-personal-data
   "第三者の個人情報の開示は窓口の裁量ではない"
   :purge-knowledge-base
   "ナレッジベースの削除は保管者の行為であり、提案の対象外"
   :delete-client-records
   "client の記録の削除は保管者の行為であり、提案の対象外"})

(defn classify
  "Classify `op` against the vocabulary. Total — every value of `op`,
  including nil and non-keywords, lands in exactly one bucket."
  [op]
  (cond
    (contains? supported op) {:status :supported :spec (get supported op)}
    (contains? reserved op)  {:status :reserved  :reason (get reserved op)}
    :else                    {:status :unsupported}))

(defn escalates?
  "Does this op always require human sign-off, independent of confidence?"
  [op]
  (boolean (get-in supported [op :escalates?])))

;; ---------------------------------------------------------------- shape

(defn- nonblank-string? [v] (and (string? v) (seq (str/trim v))))

(defn- field-fault
  "Why is `v` unusable as `field`? nil when it is usable. Kept here and
  not in the governor so that the vocabulary owns the shape of its own
  arguments."
  [field v]
  (case field
    :answer (when-not (nonblank-string? v)
              "answer は非空の文字列でなければならない（空の回答は回答ではない）")
    nil))

(defn malformed
  "Well-formedness violations of `proposal` for a SUPPORTED op. Returns a
  vector of {:rule :detail}, empty when well formed. Callers must have
  established that the op is supported — an unsupported op has no
  `:requires`, and asking this of it would return `[]`, which reads as
  `well formed`."
  [proposal]
  (let [{:keys [requires]} (get supported (:op proposal))]
    (vec
     (concat
      (for [f (sort requires)
            :when (nil? (get proposal f))]
        {:rule :incomplete-proposal
         :detail (str (name f) " が未指定（" (pr-str (:op proposal))
                      " は " (pr-str (sort requires)) " を要する）")})
      (for [f (sort requires)
            :let [v (get proposal f)]
            :when (some? v)
            :let [fault (field-fault f v)]
            :when fault]
        {:rule :ill-formed-field :detail fault})
      ;; confidence is not in :requires — it is common to every op. An
      ;; ABSENT confidence stays the low-confidence escalation it always
      ;; was; a PRESENT one outside 0..1 is not a confidence at all.
      ;; Measured on 0abfc59: 2.5 cleared the floor and was committed,
      ;; and the string "0.9" threw ClassCastException out of `check`.
      (when-let [c (:confidence proposal)]
        (when-not (and (number? c) (<= 0 c 1))
          [{:rule :ill-formed-field
            :detail (str "confidence は 0..1 の数値でなければならない（受領: "
                         (pr-str c) "）")}]))))))

;; --------------------------------------------------- datability of inputs

(defn undatable-fault
  "Why can this request's date not decide freshness? nil when it can.

  Measured on 0abfc59: the freshness rule was guarded with
  `(integer? today)`, so a request with `:today nil` — or the string
  \"20260713\" — served an entry that expired in 2023. The README calls
  the validity window \"checked DETERMINISTICALLY against the request
  date\"; it was checked only against request dates that volunteered
  themselves as integers, and omitting the date was a way to be served."
  [request]
  (when-not (integer? (:today request))
    (str ":today が無い、または整数の日付ではない（受領: " (pr-str (:today request))
         "）— 日付の無い要求に対して有効期限は判定できない")))

(defn unservable-fault
  "Why can a REGISTERED kb-entry not be judged for freshness? nil when it
  can.

  Measured on 0abfc59: an entry with `:valid-until nil` or a string
  `\"20200101\"` was served, for the same reason and with the same
  consequence. An entry that cannot state when it expires cannot show
  that it has not."
  [entry]
  (when-not (integer? (:valid-until entry))
    (str "kb entry " (pr-str (:kb-id entry)) " に整数の :valid-until が無い（受領: "
         (pr-str (:valid-until entry))
         "）— 期限を言えない項目は、期限切れでないことも言えない")))
