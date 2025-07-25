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

 (deftest test-translation-with-output-2
  (testing "Translation with components and dependencies"
    (let [;; Hardcoded forms vector (what read-file-forms would return for "Main")
          ;; Note: PaidLabel is NOT included because Main doesn't call it
          ;; Forms are in topological order (dependencies first)
          forms [{:form '(def customer-columns-xs [100 70 70]),
                  :name 'customer-columns-xs,
                  :type :def,
                  :deps #{}}
                 {:form '(defn ->class [_] ""),
                  :name '->class,
                  :type :defn,
                  :deps #{}}
                 {:form '(defn generate-absolute-style [top left]
                           {:top (r-ui/pixelate top),
                            :position "absolute",
                            :left (r-ui/pixelate left)}),
                  :name 'generate-absolute-style,
                  :type :defn,
                  :deps '#{r-ui/pixelate}}
                 {:form '(e/defn LabelAndAmount [top left text-of-label amt]
                           (e/client
                             (dom/span
                               (dom/props {:style (generate-absolute-style top left)})
                               (dom/div
                                 (dom/props
                                   {:style
                                    {:padding "5px",
                                     :display "grid",
                                     :grid-template-columns (r-ui/line-columns customer-columns-xs)},
                                    :class (->class :gen/row-indent)})
                                 (dom/div
                                   (dom/props {:class (->class :wc/customer-desc)})
                                   (dom/text text-of-label))
                                 (dom/div
                                   (dom/props
                                     {:class
                                      [(->class :wc/product-total-extension)
                                       (->class :gen/no-select)]})
                                   (dom/text amt)))))),
                  :name 'LabelAndAmount,
                  :type :e/defn,
                  :deps '#{generate-absolute-style ->class customer-columns-xs r-ui/line-columns}}
                 {:form (list 'e/defn 'Main '[ring-req]
                          (list 'e/client
                            (list 'binding ['dom/node nil
                                            'e/http-request (list 'e/server 'ring-req)]
                              (list 'dom/div
                                '(LabelAndAmount 0 0 "Some text" 20)
                                (list 'dom/h1 '(dom/text "Hello from Electric Clojure"))
                                (list 'dom/p '(dom/text "Source code for this page is in ")
                                  (list 'dom/code '(dom/text "src/electric_start_app/main.cljc")))
                                (list 'dom/p '(dom/text "Make sure you check the ")
                                  (list 'dom/a (list 'dom/props {:target "_blank", :href "https://electric.hyperfiddle.net/"})
                                    '(dom/text "Electric Tutorial"))))))),
                  :name 'Main,
                  :type :e/defn,
                  :deps '#{LabelAndAmount}}]
          result (t/translate forms "Main")
          views (t/extract-simple-forms (:views result))

          ;; Expected views - in topological order
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
      (is (= expected-views views)))))

 ; Removed duplicate test-translation-with-output-2

 (deftest test-current-translation-with-output
  (testing "Current translation handles e/defn without e/client wrapper - writes to output files"
    (let [;; Hardcoded forms vector (what read-file-forms would return for "Main")
          ;; Note: PaidLabel is NOT included because Main doesn't call it
          ;; Forms are in topological order (dependencies first)
          forms [{:form '(def customer-columns-xs [100 70 70]),
                  :name 'customer-columns-xs,
                  :type :def,
                  :deps #{}}
                 {:form '(defn ->class [_] ""),
                  :name '->class,
                  :type :defn,
                  :deps #{}}
                 {:form '(defn generate-absolute-style [top left]
                           {:top (r-ui/pixelate top),
                            :position "absolute",
                            :left (r-ui/pixelate left)}),
                  :name 'generate-absolute-style,
                  :type :defn,
                  :deps '#{r-ui/pixelate}}
                 {:form '(e/defn LabelAndAmount [top left text-of-label amt]
                           (dom/span
                            (dom/props {:style (generate-absolute-style top left)})
                            (dom/div
                             (dom/props
                              {:style
                               {:padding "5px",
                                :display "grid",
                                :grid-template-columns (r-ui/line-columns customer-columns-xs)},
                               :class (->class :gen/row-indent)})
                             (dom/div
                              (dom/props {:class (->class :wc/customer-desc)})
                              (dom/text text-of-label))
                             (dom/div
                              (dom/props
                               {:class
                                [(->class :wc/product-total-extension)
                                 (->class :gen/no-select)]})
                              (dom/text amt))))),
                  :name 'LabelAndAmount,
                  :type :e/defn,
                  :deps '#{generate-absolute-style ->class customer-columns-xs r-ui/line-columns}}
                 {:form (list 'e/defn 'Main '[ring-req]
                          (list 'e/client
                            (list 'binding ['dom/node nil
                                            'e/http-request (list 'e/server 'ring-req)]
                              (list 'dom/div
                               '(LabelAndAmount 0 0 "Some text" 20)
                               (list 'dom/h1 '(dom/text "Hello from Electric Clojure"))
                               (list 'dom/p '(dom/text "Source code for this page is in ")
                                 (list 'dom/code '(dom/text "src/electric_start_app/main.cljc")))
                               (list 'dom/p '(dom/text "Make sure you check the ")
                                 (list 'dom/a (list 'dom/props {:target "_blank", :href "https://electric.hyperfiddle.net/"})
                                   '(dom/text "Electric Tutorial"))))))),
                  :name 'Main,
                  :type :e/defn,
                  :deps '#{LabelAndAmount}}]
          result (t/translate forms "Main" "reframe-output")
          views (t/extract-simple-forms (:views result))

          ;; Expected views - in topological order
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
      (is (= expected-views views)))))

