(ns translator.error-handling-test
  (:require [clojure.test :refer [deftest testing is]]
            [translator.translator :as t]))

(deftest test-unsupported-when-form
  (testing "Translation fails gracefully for 'when' forms containing DOM elements"
    (let [form '(e/defn TestWhen []
                  (e/client
                    (dom/div
                      (when true
                        (dom/div (dom/text "This uses when"))))))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Unsupported form 'when' containing DOM elements"
                            (t/translate form))))))

(deftest test-unsupported-cond-form
  (testing "Translation fails gracefully for 'cond' forms containing DOM elements"
    (let [form '(e/defn TestCond []
                  (e/client
                    (dom/div
                      (cond
                        true (dom/div (dom/text "First"))
                        :else (dom/div (dom/text "Else"))))))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Unsupported form 'cond' containing DOM elements"
                            (t/translate form))))))

(deftest test-unsupported-for-form
  (testing "Translation fails gracefully for 'for' forms containing DOM elements"
    (let [form '(e/defn TestFor []
                  (e/client
                    (dom/div
                      (for [x [1 2 3]]
                        (dom/div (dom/text x))))))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Unsupported form 'for' containing DOM elements"
                            (t/translate form))))))

(deftest test-supported-when-without-dom
  (testing "Translation passes through 'when' forms that don't contain DOM elements"
    (let [form '(e/defn TestWhen []
                  (e/client
                    (let [show? true]
                      (dom/div
                        (dom/text (when show? "Visible"))))))]
      ;; Should not throw
      (let [result (t/translate form)]
        (is (= 1 (count (:views result))))))))

(deftest test-svg-elements-unsupported
  (testing "Translation fails gracefully for SVG elements"
    (let [form '(e/defn TestSVG []
                  (e/client
                    (svg/svg
                      (svg/circle {:cx 50 :cy 50 :r 40}))))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"SVG elements are not yet supported"
                            (t/translate form))))))

(deftest test-error-data-contains-details
  (testing "Errors contain helpful data about what's unsupported"
    (let [form '(e/defn TestWhen []
                  (e/client
                    (dom/div
                      (when true
                        (dom/div (dom/text "test"))))))]
      (try
        (t/translate form)
        (is false "Should have thrown an exception")
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (is (= 'when (:unsupported-form data)))
            (is (contains? data :form))
            (is (= #{'let 'if} (:supported-forms data)))))))))

(deftest test-unknown-namespace-alias
  (testing "Translation fails gracefully for unknown namespace aliases in event handlers"
    (let [form '(e/defn TestUnknownNS []
                  (e/client
                    (dom/button
                      (dom/On "click" #(unknown-ns/mutation some-ns/action []) nil)
                      (dom/text "Click me"))))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Unknown namespace alias: some-ns"
                            (t/translate form)))))
  
  (testing "Error data contains available aliases"
    (let [form '(e/defn TestUnknownNS []
                  (e/client
                    (dom/button
                      (dom/On "click" #(unknown-ns/mutation some-ns/action []) nil)
                      (dom/text "Click me"))))]
      (try
        (t/translate form)
        (is false "Should have thrown an exception")
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (is (= 'some-ns (:alias data)))
            (is (contains? data :available-aliases))
            (is (contains? (set (:available-aliases data)) 'r-events))))))))
