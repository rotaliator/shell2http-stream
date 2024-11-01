(ns shell2http-stream.main-test
  (:require [shell2http-stream.main :as sut]
            [clojure.test :as t]
            [clojure.core.async :as async]))

(defn ^:private acc-chan [source-chan]
  (async/go-loop [accumulator []]
    (let [item (async/<! source-chan)]
      (if (nil? item)
        accumulator
        (recur (conj accumulator item))))))


(defn ^:private grab-async-body!! [handler request]
  (let [result   (promise)
        _        (handler request #(deliver result %) (partial ex-info "Exception"))]
    (async/<!! (acc-chan (:body @result)))))

(t/deftest no-index-test
  (t/testing "no-index"
    (t/is (= 200 (:status (sut/main-handler {:uri "/"}))))
    (t/is (= 404 (:status (sut/main-handler {:uri "/" ::sut/options (sut/options-from-args ["--no-index"])}))))))

(t/deftest basic-test
  (t/testing "basic"
    (let [command "echo hello world!"
          options (sut/options-from-args ["/test" command])
          body    (grab-async-body!! sut/async-handler
                                     {:uri "/test" ::sut/options options})]
      (t/is (= "hello world!\n" (first body))))))

(t/deftest form-test
  (t/testing "form"
    (let [options (sut/options-from-args ["--form" "/form" "env"])
          request {:uri "/form" :query-string "parameter1=value1&parameter2=value2" ::sut/options options}
          body    (set (grab-async-body!! sut/async-handler request))]
      (t/is (contains? body "parameter1=value1\n"))
      (t/is (contains? body "parameter2=value2\n")))))
