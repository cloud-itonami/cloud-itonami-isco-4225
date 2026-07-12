(ns inquiry.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [inquiry.store :as store]
            [inquiry.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "City Library"})
    (store/register-kb-entry! st {:kb-id "kb-1" :client-id "client-1"
                                  :topic "opening hours"
                                  :answer "9:00-17:00 weekdays"
                                  :valid-until 20401231})
    (store/register-kb-entry! st {:kb-id "kb-old" :client-id "client-1"
                                  :topic "old covid policy"
                                  :answer "masks required"
                                  :valid-until 20230331})
    st))

(defn- answer [kb-id]
  {:op :answer-inquiry :effect :propose :kb-id kb-id
   :answer "from kb" :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1" :today 20260713})

(deftest ok-on-cited-fresh-answer
  (let [st (fresh-store)
        v (governor/check req {} (answer "kb-1") st)]
    (is (:ok? v))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody" :today 20260713} {} (answer "kb-1") st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (answer "kb-1") :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-uncited-answer
  (testing "an answer without a KB citation is an invented answer"
    (let [st (fresh-store)
          v (governor/check req {} (answer nil) st)]
      (is (:hard? v))
      (is (some #(= :no-kb-citation (:rule %)) (:violations v))))))

(deftest hard-on-unknown-kb-entry
  (let [st (fresh-store)
        v (governor/check req {} (answer "kb-999") st)]
    (is (:hard? v))
    (is (some #(= :unknown-kb-entry (:rule %)) (:violations v)))))

(deftest hard-on-kb-of-another-client
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other Org"})
    (let [v (governor/check {:client-id "client-2" :today 20260713} {} (answer "kb-1") st)]
      (is (:hard? v))
      (is (some #(= :kb-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-stale-knowledge
  (testing "expired knowledge is not servable at any confidence — refresh the KB"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (answer "kb-old") :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :stale-knowledge (:rule %)) (:violations v))))))

(deftest escalates-faq-publication
  (let [st (fresh-store)
        v (governor/check req {} {:op :publish-faq :effect :propose
                                  :confidence 0.9 :stake :medium} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} {:op :log-inquiry :effect :propose
                                  :confidence 0.3 :stake :low} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
