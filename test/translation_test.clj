(ns translation-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as string]
            [translator :as t]))

(deftest test-simple-electric-to-reframe-translation
  (testing "Translation of basic Electric DOM elements to Re-frame views"
    (let [electric-code '(e/defn Main [ring-request]
                          (e/client
                            (binding [dom/node js/document.body]
                              (dom/h1 (dom/text "Hello from Electric Clojure"))
                              (dom/p (dom/text "Source code for this page is in ")
                                     (dom/code (dom/text "src/electric_start_app/main.cljc")))
                              (dom/p (dom/text "Make sure you check the ")
                                (dom/a (dom/props {:href "https://electric.hyperfiddle.net/" :target "_blank"})
                                  (dom/text "Electric Tutorial"))))))
          
          result (t/translate electric-code)
          
          expected-view '(defn main-view []
                           [:<>
                            [:h1 "Hello from Electric Clojure"]
                            [:p "Source code for this page is in "
                             [:code "src/electric_start_app/main.cljc"]]
                            [:p "Make sure you check the "
                             [:a {:href "https://electric.hyperfiddle.net/" :target "_blank"}
                              "Electric Tutorial"]]])]
      
      ;; Check that all three keys exist
      (is (contains? result :views))
      (is (contains? result :events))
      (is (contains? result :subs))
      
      ;; Check that views contains our expected form
      (is (= 1 (count (:views result))))
      (is (= expected-view (first (:views result))))
      
      ;; For this simple example, events and subs should be empty
      (is (empty? (:events result)))
      (is (empty? (:subs result))))))

(deftest test-empty-electric-function
  (testing "Translation of empty Electric function"
    (let [electric-code '(e/defn Empty [ring-request]
                          (e/client
                            (binding [dom/node js/document.body])))
          
          result (t/translate electric-code)
          
          expected-view '(defn empty-view []
                           [:<>])]
      
      (is (= 1 (count (:views result))))
      (is (= expected-view (first (:views result))))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))))

(deftest test-single-element
  (testing "Translation of Electric function with single element"
    (let [electric-code '(e/defn SingleElement [ring-request]
                          (e/client
                            (binding [dom/node js/document.body]
                              (dom/div (dom/text "Single element")))))
          
          result (t/translate electric-code)
          
          ;; Single element doesn't need React Fragment wrapper
          expected-view '(defn single-element-view []
                           [:div "Single element"])]
      
      (is (= 1 (count (:views result))))
      (is (= expected-view (first (:views result))))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))))

(deftest test-nested-elements
  (testing "Translation of nested Electric DOM elements"
    (let [electric-code '(e/defn Nested [ring-request]
                          (e/client
                            (binding [dom/node js/document.body]
                              (dom/div 
                                (dom/h1 (dom/text "Title"))
                                (dom/p (dom/text "Paragraph"))))))
          
          result (t/translate electric-code)
          
          expected-view '(defn nested-view []
                           [:div
                            [:h1 "Title"]
                            [:p "Paragraph"]])]
      
      (is (= 1 (count (:views result))))
      (is (= expected-view (first (:views result))))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))))

(deftest test-current-translation-with-output
  (testing "Current translation - writes to output files"
    (let [electric-code '(e/defn Main [ring-request]
                          (e/client
                            (binding [dom/node js/document.body]
                              (dom/h1 (dom/text "Hello from Electric Clojure"))
                              (dom/p (dom/text "Source code for this page is in ")
                                     (dom/code (dom/text "src/electric_start_app/main.cljc")))
                              (dom/p (dom/text "Make sure you check the ")
                                (dom/a (dom/props {:href "https://electric.hyperfiddle.net/" :target "_blank"})
                                  (dom/text "Electric Tutorial"))))))
          
          ;; Call translate with output-ns to write files
          result (t/translate electric-code "reframe-output")
          
          expected-view '(defn main-view []
                           [:<>
                            [:h1 "Hello from Electric Clojure"]
                            [:p "Source code for this page is in "
                             [:code "src/electric_start_app/main.cljc"]]
                            [:p "Make sure you check the "
                             [:a {:href "https://electric.hyperfiddle.net/" :target "_blank"}
                              "Electric Tutorial"]]])]
      
      ;; Verify the result is correct
      (is (= 1 (count (:views result))))
      (is (= expected-view (first (:views result))))
      (is (empty? (:events result)))
      (is (empty? (:subs result)))
      
      ;; Verify the file was written by reading it back
      (let [written-content (slurp "reframe-output/reframe_output/views.cljs")]
        (is (string? written-content))
        (is (string/includes? written-content "ns reframe-output.views"))
        (is (string/includes? written-content "defn"))
        (is (string/includes? written-content "main-view"))))))
