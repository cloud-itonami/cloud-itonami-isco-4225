(ns inquiry.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [inquiry.actor :as actor]
            [inquiry.ledger :as ledger]
            [inquiry.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "City Library"})
    (store/register-kb-entry! st {:kb-id "kb-1" :client-id "client-1"
                                  :topic "opening hours"
                                  :answer "9:00-17:00 weekdays"
                                  :valid-until 20401231})
    st))

(deftest commits-a-cited-fresh-answer
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :today 20260713 :op :answer-inquiry
                 :stake :low :kb-id "kb-1" :answer "9:00-17:00 weekdays"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-invented-answer-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :today 20260713 :op :answer-inquiry
                 :stake :low :kb-id nil :answer "making something up"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-publishes-faq-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :today 20260713 :op :publish-faq
                 :stake :medium}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))

;; ------------------------------------------------------------------
;; End-to-end: what the ledger can now answer that it could not on
;; 0abfc59, and the commits that used to happen and no longer do.
;; ------------------------------------------------------------------

(defn- ledger-of [st] (store/ledger st))

(deftest an-automatic-commit-names-the-governor-in-the-ledger
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :today 20260713 :op :answer-inquiry
                 :stake :low :kb-id "kb-1" :answer "9:00-17:00 weekdays"}]
    (actor/run-request! graph request {} "led-1")
    (let [[e :as entries] (ledger-of st)]
      (is (= 1 (count entries)))
      (is (= :commit (:disposition e)))
      (is (= :governor-clear (:authorisation e)))
      (is (not (ledger/human-signed? e))))))

(deftest a-human-approved-publication-is-visible-as-such-in-the-ledger
  (testing "the defect this closes: on 0abfc59 this entry was byte-identical
            to the automatic one, and :publish-faq ALWAYS requires sign-off"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:client-id "client-1" :today 20260713 :op :publish-faq
                   :stake :medium}]
      (actor/run-request! graph request {} "led-2")
      (is (empty? (ledger-of st)) "nothing is written while it waits for a human")
      (actor/approve! graph "led-2")
      (let [[e :as entries] (ledger-of st)]
        (is (= 1 (count entries)))
        (is (= :commit (:disposition e)))
        (is (= :human-sign-off (:authorisation e)))
        (is (ledger/human-signed? e))))))

(deftest the-two-committed-entries-do-not-look-alike
  (testing "asked of the ledger alone, without knowing which run wrote which"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})]
      (actor/run-request! graph {:client-id "client-1" :today 20260713
                                 :op :answer-inquiry :stake :low
                                 :kb-id "kb-1" :answer "9:00-17:00 weekdays"}
                          {} "led-3a")
      (actor/run-request! graph {:client-id "client-1" :today 20260713
                                 :op :publish-faq :stake :medium} {} "led-3b")
      (actor/approve! graph "led-3b")
      (let [entries (ledger-of st)]
        (is (= 2 (count entries)))
        (is (= 1 (count (filter ledger/human-signed? entries))))
        (is (= #{:governor-clear :human-sign-off}
               (set (map ledger/authorisation-of entries))))))))

(deftest a-hold-is-recorded-with-its-refusal-reason
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})]
    (actor/run-request! graph {:client-id "client-1" :today 20260713
                              :op :answer-inquiry :stake :low
                              :kb-id nil :answer "making something up"}
                        {} "led-4")
    (let [[e] (ledger-of st)]
      (is (= :hold (:disposition e)))
      (is (= :governor-hold (:authorisation e)))
      (is (some #(= :no-kb-citation (:rule %)) (get-in e [:verdict :violations]))))))

(deftest the-actor-no-longer-commits-an-undeclared-op
  (testing "measured on 0abfc59: :purge-knowledge-base and :delete-client-records
            each produced :done with 1 record committed"
    (doseq [op [:purge-knowledge-base :delete-client-records :not-a-desk-op]]
      (let [st (fresh-store)
            graph (actor/build-graph {:store st})]
        (actor/run-request! graph {:client-id "client-1" :today 20260713
                                   :op op :stake :low} {} (str "led-5-" (name op)))
        (is (empty? (store/records-of st "client-1"))
            (str "committed an undeclared op: " op))
        (is (= :hold (:disposition (first (ledger-of st)))))))))

(deftest a-nil-op-is-refused-rather-than-crashing-the-run
  (testing "measured on 0abfc59: NullPointerException in advisor/infer, which
            runs BEFORE the governor — so there was no verdict and no entry"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          result (actor/run-request! graph {:client-id "client-1" :today 20260713
                                            :op nil :stake :low} {} "led-6")]
      (is (= :done (:status result)))
      (is (empty? (store/records-of st "client-1")))
      (is (= :hold (:disposition (first (ledger-of st))))))))

(deftest the-actor-no-longer-serves-expired-knowledge-without-a-date
  (testing "measured on 0abfc59: :today nil committed an answer from kb-old"
    (let [st (fresh-store)]
      (store/register-kb-entry! st {:kb-id "kb-old" :client-id "client-1"
                                    :topic "old policy" :answer "masks required"
                                    :valid-until 20230331})
      (let [graph (actor/build-graph {:store st})]
        (actor/run-request! graph {:client-id "client-1" :today nil
                                   :op :answer-inquiry :stake :low
                                   :kb-id "kb-old" :answer "masks required"}
                            {} "led-7")
        (is (empty? (store/records-of st "client-1")))
        (is (some #(= :undatable-request (:rule %))
                  (get-in (first (ledger-of st)) [:verdict :violations])))))))
