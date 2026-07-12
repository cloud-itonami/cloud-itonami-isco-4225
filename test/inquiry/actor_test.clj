(ns inquiry.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [inquiry.actor :as actor]
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
