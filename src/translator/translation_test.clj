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

 (deftest test-translation-with-output-3
  (testing "Translation handles e/defn without e/client wrapper"
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
          ;; Note: NO output-ns parameter here
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

 (deftest test-translation-with-output-4
  (testing "Translation handles let bindings in e/defn forms"
    (let [;; Define the forms directly (what would come from reading the file)
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
                 {:form '(e/defn LabelAndAmountNew
                           [top left text-of-label amt]
                           (let [span-style (generate-absolute-style top left)
                                 div-class (->class :gen/row-indent)]
                             (dom/span
                               (dom/props {:style span-style})
                               (dom/div
                                 (dom/props {:class div-class
                                             :style {:display "grid"
                                                     :grid-template-columns (r-ui/line-columns customer-columns-xs)
                                                     :padding "5px"}})
                                 (dom/div
                                   (dom/props {:class (->class :wc/customer-desc)})
                                   (dom/text text-of-label))
                                 (dom/div
                                   (dom/props {:class [(->class :wc/product-total-extension)
                                                       (->class :gen/no-select)]})
                                   (dom/text amt)))))),
                  :name 'LabelAndAmountNew,
                  :type :e/defn,
                  :deps '#{generate-absolute-style ->class customer-columns-xs r-ui/line-columns}}
                 {:form (list 'e/defn 'Main '[ring-req]
                          (list 'e/client
                            (list 'binding ['dom/node nil
                                            'e/http-request (list 'e/server 'ring-req)]
                              (list 'dom/div
                                '(LabelAndAmountNew 0 0 "Some text" 20)
                                (list 'dom/h1 '(dom/text "Hello from Electric Clojure"))
                                (list 'dom/p '(dom/text "Source code for this page is in ")
                                  (list 'dom/code '(dom/text "src/electric_start_app/main.cljc")))
                                (list 'dom/p '(dom/text "Make sure you check the ")
                                  (list 'dom/a (list 'dom/props {:target "_blank", :href "https://electric.hyperfiddle.net/"})
                                    '(dom/text "Electric Tutorial"))))))),
                  :name 'Main,
                  :type :e/defn,
                  :deps '#{LabelAndAmountNew}}]
          
          ;; Call translate WITHOUT output-ns - no file writing
          result (t/translate forms "Main")
          views (t/extract-simple-forms (:views result))

          ;; Find the label-and-amount-new-view function
          label-and-amount-fn (first (filter #(and (seq? %)
                                                   (= 'defn (first %))
                                                   (= 'label-and-amount-new-view (second %)))
                                           views))]

      ;; Verify the function exists
      (is (some? label-and-amount-fn))

      ;; Check that the let binding is preserved
      (when label-and-amount-fn
        (let [[_ _ params & body] label-and-amount-fn
              first-form (first body)]
          ;; The body should start with a let form
          (is (= 'let (first first-form)))

          ;; Check the let bindings
          (let [bindings (second first-form)]
            (is (vector? bindings))
            (is (= 'span-style (first bindings)))
            (is (= 'div-class (nth bindings 2))))

          ;; The let body should contain the hiccup vector
          (let [let-body (drop 2 first-form)]
            (is (= 1 (count let-body)))
            (is (vector? (first let-body)))
            (is (= :span (first (first let-body)))))))

      ;; Verify all views are generated (including Main)
      (is (= 5 (count views)))))) 

(deftest test-current-svg-and-complex-forms
  (testing "Current translation handles SVG, e/for, plain defns, and complex nested structures - writes to output files"
    (let [;; Electric forms from the actual Main file - simplified to focus on new patterns
          forms [{:form '(defn ->class [_] ""),
                  :name '->class,
                  :type :defn,
                  :deps #{}}
                 {:form '(e/defn OverlayAsPaths [z-index n dims]
                           (when (> n 1)
                             (dom/div
                               (dom/props {:style {:pointer-events "none"
                                                  :position "absolute"
                                                  :z-index z-index}})
                               (svg/svg
                                 (e/for [[opacity rect-path] (e/diff-by identity (map vector utl/opacities (utl/rect->path-dims dims n)))]
                                   (svg/path
                                     (dom/props {:opacity opacity
                                                 :d rect-path}))))))),
                  :name 'OverlayAsPaths,
                  :type :e/defn,
                  :deps '#{utl/opacities utl/rect->path-dims}}
                 {:form '(def config {:restaurant {:b? true}}),
                  :name 'config,
                  :type :def,
                  :deps #{}}
                 {:form (list 'e/defn 'Main '[ring-req]
                          (list 'e/client
                            (list 'binding ['dom/node 'js/document.body]
                              (list 'dom/div
                                '(OverlayAsPaths 10 2 {:width 100 :height 50}))))),
                  :name 'Main,
                  :type :e/defn,
                  :deps '#{OverlayAsPaths}}]
          
          ;; Call translate with output-ns to write files
          result (t/translate forms "Main" "reframe-output")
          views (t/extract-simple-forms (:views result))]

      ;; Verify functions are generated
      (is (= 4 (count views))) ; ->class, OverlayAsPaths, config, Main
      
      ;; Find specific functions to verify structure
      (let [overlay-fn (first (filter #(and (seq? %)
                                           (= 'defn (first %))
                                           (= 'overlay-as-paths (second %)))
                                     views))
            config-def (first (filter #(and (seq? %)
                                          (= 'def (first %))
                                          (= 'config (second %)))
                                    views))]
        
        ;; Check OverlayAsPaths function has SVG elements
        (when overlay-fn
          (let [[_ _ params & body] overlay-fn
                when-form (first body)]
            (is (= 'when (first when-form)))
            (let [div-form (nth when-form 2)]
              (is (vector? div-form))
              (is (= :div (first div-form)))
              ;; Check for SVG element
              (let [svg-form (nth div-form 2)]
                (is (vector? svg-form))
                (is (= :svg (first svg-form)))
                ;; Check for for loop
                (let [for-expr (second svg-form)]
                  (is (seq? for-expr))
                  (is (= 'for (first for-expr))))))))
        
        ;; Check that config def is preserved
        (is (some? config-def))
        (when config-def
          (is (= 'def (first config-def)))
          (is (= 'config (second config-def)))
          (is (map? (nth config-def 2)))))
      
      ;; Verify files were written
      (let [views-content (slurp "reframe-output/reframe_output/views.cljs")]
        (is (string/includes? views-content "overlay-as-paths"))
        (is (string/includes? views-content ":svg"))
        (is (string/includes? views-content "^{:key"))))))
