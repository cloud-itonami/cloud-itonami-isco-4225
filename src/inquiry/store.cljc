(ns inquiry.store
  "SSoT for the ISCO-08 4225 community inquiry-desk actor (itonami
  actor pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client    — a registered organization the desk answers for
                (:client-id, :name)
    kb-entry  — a registered knowledge-base entry {:kb-id :client-id
                :topic :answer :valid-until} (integer day number).
                The ONLY admissible basis for an answer — an answer
                without a KB citation is an invented answer, and stale
                knowledge (past :valid-until) may not be served.
    record    — a committed operating record (answered inquiry, logged
                inquiry, published FAQ) — written ONLY via commit-record!.
    ledger    — append-only audit trail of every proposal/verdict/
                disposition, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (kb-entry [s kb-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-kb-entry! [s entry])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (kb-entry [_ kb-id] (get-in @a [:kb kb-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-kb-entry! [s entry]
    (swap! a assoc-in [:kb (:kb-id entry)] entry) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :kb {} :records [] :ledger []}
                                   seed)))))
