(ns translator.translation-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as string]
            [translator.translator :as t]))

(deftest test-direct-dom-translation
  (testing "Translation of direct DOM forms without e/defn wrapper"
    ;; Test single DOM element
    (let [dom-form '(dom/div (dom/text "Hello"))
          result (t/translate dom-form)
          views (t/extract-simple-forms (:views result))]
      (is (= [:div "Hello"] (first views)))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))

    ;; Test multiple DOM elements (should wrap in fragment)
    (let [dom-forms '((dom/h1 (dom/text "Title"))
                      (dom/p (dom/text "Paragraph")))
          result (t/translate dom-forms)
          views (t/extract-simple-forms (:views result))]
      (is (= [:<> [:h1 "Title"] [:p "Paragraph"]]
             (first views)))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))

    ;; Test DOM element with props
    (let [dom-form '(dom/a (dom/props {:href "http://example.com"})
                           (dom/text "Link"))
          result (t/translate dom-form)
          views (t/extract-simple-forms (:views result))]
      (is (= [:a {:href "http://example.com"} "Link"]
             (first views)))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))))

(deftest test-empty-electric-function
  (testing "Translation of empty Electric function"
    (let [electric-code '(e/defn Empty [ring-request]
                          (e/client
                            (binding [dom/node js/document.body])))
          result (t/translate electric-code)
          views (t/extract-simple-forms (:views result))
          expected-view '(defn empty-view [ring-request] [:<>])]

      (is (= 1 (count views)))
      (is (= expected-view (first views)))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))))

(deftest test-single-element
  (testing "Translation of Electric function with single element"
    (let [dom-form '(dom/div (dom/text "Single element"))
          result (t/translate dom-form)
          views (t/extract-simple-forms (:views result))
          ;; Single element doesn't need React Fragment wrapper
          expected [:div "Single element"]]

      (is (= 1 (count views)))
      (is (= expected (first views)))
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
          views (t/extract-simple-forms (:views result))
          expected-view '(defn nested-view [ring-request]
                           [:div
                            [:h1 "Title"]
                            [:p "Paragraph"]])]

      (is (= 1 (count views)))
      (is (= expected-view (first views)))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))))

(deftest test-translation-with-output-1
  (testing "Basic DOM translation"
    (let [dom-forms '((dom/h1 (dom/text "Hello from Electric Clojure"))
                      (dom/p (dom/text "Source code for this page is in ")
                        (dom/code (dom/text "src/electric_start_app/main.cljc")))
                      (dom/p (dom/text "Make sure you check the ")
                        (dom/a (dom/props {:href "https://electric.hyperfiddle.net/" :target "_blank"})
                          (dom/text "Electric Tutorial"))))

          ;; Call translate WITHOUT output-ns - not the current test
          result (t/translate dom-forms)
          views (t/extract-simple-forms (:views result))]

      ;; Verify the result is correct - just test the views vector directly
      (is (= [[:<>
               [:h1 "Hello from Electric Clojure"]
               [:p "Source code for this page is in "
                [:code "src/electric_start_app/main.cljc"]]
               [:p "Make sure you check the "
                [:a {:href "https://electric.hyperfiddle.net/" :target "_blank"}
                 "Electric Tutorial"]]]]
             views))
      (is (empty? (:events result)))
      (is (empty? (:subs result))))))

 (deftest test-current-translation-with-output
  (testing "Current translation with components - writes to output files"
    (let [;; Use translate with file path, starting function, AND output-ns
          result (t/translate "electric-src/electric_starter_app/main.cljc" "Main" "reframe-output")
          views (t/extract-simple-forms (:views result))

          ;; Expected views - matching what the translator produces
          expected-views ['(def customer-columns-xs [100 70 70])

                          '(defn ->class [_] "")

                          '(defn generate-absolute-style [top left]
                             {:position "absolute"
                              :top (r-ui/pixelate top)
                              :left (r-ui/pixelate left)})

                          '(defn label-and-amount-view [top left text-of-label amt]
                             [:span
                              {:style (generate-absolute-style top left)}
                              [:div
                               {:class (->class :gen/row-indent)
                                :style {:display "grid"
                                        :grid-template-columns (r-ui/line-columns customer-columns-xs)
                                        :padding "5px"}}
                               [:div
                                {:class (->class :wc/customer-desc)}
                                text-of-label]
                               [:div
                                {:class [(->class :wc/product-total-extension) (->class :gen/no-select)]}
                                amt]]])

                          '(defn main-view [ring-req]
                             [:div
                              [label-and-amount-view 0 0 "Some text" 20]
                              [:h1 "Hello from Electric Clojure"]
                              [:p "Source code for this page is in "
                               [:code "src/electric_start_app/main.cljc"]]
                              [:p "Make sure you check the "
                               [:a {:href "https://electric.hyperfiddle.net/" :target "_blank"}
                                "Electric Tutorial"]]])]]

      ;; Verify we got the expected functions
      (is (= 5 (count views)))

      ;; Verify each view matches expected output
      (is (= expected-views views))
      )))
