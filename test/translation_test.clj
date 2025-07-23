(ns translation-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as string]
            [translator :as t]))

(deftest test-current-translation-with-output
  (testing "Current translation - writes to output files"
    (let [dom-forms '((dom/h1 (dom/text "Hello from Electric Clojure"))
                      (dom/p (dom/text "Source code for this page is in ")
                             (dom/code (dom/text "src/electric_start_app/main.cljc")))
                      (dom/p (dom/text "Make sure you check the ")
                        (dom/a (dom/props {:href "https://electric.hyperfiddle.net/" :target "_blank"})
                          (dom/text "Electric Tutorial"))))
          
          ;; Call translate with output-ns to write files
          result (t/translate dom-forms "reframe-output")
          
          expected [:<>
                    [:h1 "Hello from Electric Clojure"]
                    [:p "Source code for this page is in "
                     [:code "src/electric_start_app/main.cljc"]]
                    [:p "Make sure you check the "
                     [:a {:href "https://electric.hyperfiddle.net/" :target "_blank"}
                      "Electric Tutorial"]]]]
      
      ;; Verify the result is correct
      (is (= 1 (count (:views result))))
      (is (= expected (first (:views result))))
      (is (empty? (:events result)))
      (is (empty? (:subs result)))
      
      ;; Verify the file was written by reading it back
      (let [written-content (slurp "reframe-output/reframe_output/views.cljs")]
        (is (string? written-content))
        (is (string/includes? written-content "ns reframe-output.views"))
        (is (string/includes? written-content "[:<>"))))))

(deftest test-direct-dom-translation
  (testing "Translation of direct DOM forms without e/defn wrapper"
    ;; Test single DOM element
    (let [dom-form '(dom/div (dom/text "Hello"))
          result (t/translate dom-form)]
      (is (= [:div "Hello"] (first (:views result))))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))
    
    ;; Test multiple DOM elements (should wrap in fragment)
    (let [dom-forms '((dom/h1 (dom/text "Title"))
                      (dom/p (dom/text "Paragraph")))
          result (t/translate dom-forms)]
      (is (= [:<> [:h1 "Title"] [:p "Paragraph"]] 
             (first (:views result))))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))
    
    ;; Test DOM element with props
    (let [dom-form '(dom/a (dom/props {:href "http://example.com"})
                           (dom/text "Link"))
          result (t/translate dom-form)]
      (is (= [:a {:href "http://example.com"} "Link"] 
             (first (:views result))))
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
    (let [dom-form '(dom/div (dom/text "Single element"))
          result (t/translate dom-form)
          ;; Single element doesn't need React Fragment wrapper
          expected [:div "Single element"]]
      
      (is (= 1 (count (:views result))))
      (is (= expected (first (:views result))))
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
