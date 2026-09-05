(ns inquiry.ledger-test
  "The ledger exists to answer one question: what authorised this write?

  Measured on 0abfc59, an automatic commit and one reached through
  `actor/approve!` produced entries with identical keys
  `(:disposition :record)`. The README says :publish-faq — external
  publication — ALWAYS requires human sign-off, and no entry could show
  that it had received it."
  (:require [clojure.test :refer [deftest is testing]]
            [inquiry.ledger :as ledger]))

(defn- fault-of [m]
  (try (ledger/entry m) nil
       (catch clojure.lang.ExceptionInfo e (:fault (ex-data e)))))

(deftest a-cleared-commit-names-the-governor
  (let [e (ledger/entry {:disposition :commit :authorisation :governor-clear
                         :record {:op :answer-inquiry}})]
    (is (= :governor-clear (ledger/authorisation-of e)))
    (is (not (ledger/human-signed? e)))))

(deftest a-resumed-commit-names-the-human
  (let [e (ledger/entry {:disposition :commit :authorisation :human-sign-off
                         :record {:op :publish-faq}})]
    (is (= :human-sign-off (ledger/authorisation-of e)))
    (is (ledger/human-signed? e))))

(deftest the-two-commits-are-distinguishable
  (testing "the defect this namespace exists to close"
    (let [auto  (ledger/entry {:disposition :commit :authorisation :governor-clear
                               :record {:op :publish-faq}})
          human (ledger/entry {:disposition :commit :authorisation :human-sign-off
                               :record {:op :publish-faq}})]
      (is (not= auto human))
      (is (not= (ledger/authorisation-of auto) (ledger/authorisation-of human)))
      (testing "and identical payloads do not make them look alike"
        (is (= (:record auto) (:record human)))))))

(deftest a-hold-names-the-governor-that-refused
  (let [e (ledger/entry {:disposition :hold :authorisation :governor-hold
                         :verdict {:ok? false :hard? true
                                   :violations [{:rule :no-kb-citation}]}})]
    (is (= :governor-hold (ledger/authorisation-of e)))
    (is (not (ledger/human-signed? e)))
    (testing "the refusal reason survives into the entry"
      (is (= [{:rule :no-kb-citation}] (get-in e [:verdict :violations]))))))

;; ---------------------------------------------- refusals, by their own name

(deftest an-entry-without-an-authorisation-is-refused
  (testing "the exact defect: an entry that does not say what permitted it"
    (is (some? (fault-of {:disposition :commit :record {:op :answer-inquiry}})))
    (is (re-find #":authorisation"
                 (fault-of {:disposition :commit :record {:op :answer-inquiry}})))))

(deftest an-unknown-authorisation-is-refused
  (is (re-find #":authorisation"
               (fault-of {:disposition :commit :authorisation :looked-fine-to-me
                          :record {:op :answer-inquiry}}))))

(deftest a-hold-cannot-claim-human-sign-off
  (testing "nothing was committed, so nobody signed anything off"
    (is (some? (fault-of {:disposition :hold :authorisation :human-sign-off
                          :verdict {:hard? true}})))))

(deftest a-commit-cannot-be-authorised-by-a-hold
  (is (some? (fault-of {:disposition :commit :authorisation :governor-hold
                        :record {:op :answer-inquiry}}))))

(deftest a-commit-without-a-record-is-refused
  (is (some? (fault-of {:disposition :commit :authorisation :governor-clear}))))

(deftest a-hold-without-a-verdict-is-refused
  (testing "a refusal that does not carry its reason is not an audit entry"
    (is (some? (fault-of {:disposition :hold :authorisation :governor-hold})))))

(deftest an-unknown-disposition-is-refused
  (doseq [d [nil :maybe "commit"]]
    (is (some? (fault-of {:disposition d :authorisation :governor-clear
                          :record {:op :answer-inquiry}}))
        (str "admitted as a disposition: " (pr-str d)))))

(deftest entry-is-pure
  (testing "building an entry is not appending one — it touches no store"
    (is (= {:disposition :commit :authorisation :governor-clear :record {:op :log-inquiry}}
           (ledger/entry {:disposition :commit :authorisation :governor-clear
                          :record {:op :log-inquiry}})))))

(deftest a-pre-existing-entry-reports-nil-not-a-guess
  (testing "entries written before this namespace existed said nothing;
            nil is the honest answer and is not :governor-clear"
    (is (nil? (ledger/authorisation-of {:disposition :commit :record {}})))
    (is (not (ledger/human-signed? {:disposition :commit :record {}})))))
