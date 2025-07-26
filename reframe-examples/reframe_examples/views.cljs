(ns reframe-examples.views
  (:require
   [restaurant.ui :as r-ui]))

(def customer-columns-xs [100 70 70])

(defn ->class [_]
  "")

;; From wc-ui/ normally
(defn generate-absolute-style [top left]
  {:position "absolute"
   :top (r-ui/pixelate top)
   :left (r-ui/pixelate left)})

(defn label-and-amount
  [top left text-of-label amt]
  (let [span-style (generate-absolute-style top left)
        div-class (->class :gen/row-indent)]
    [:span
     {:style span-style}
     [:div
      {:class div-class
       :style {:display "grid"
               :grid-template-columns (r-ui/line-columns customer-columns-xs)
               :padding "5px"}}
      [:div
       {:class (->class :wc/customer-desc)}
       text-of-label]
      [:div
       {:class [(->class :wc/product-total-extension) (->class :gen/no-select)]}
       amt]]
     ]))

(defn main-view []
  [:div
   [label-and-amount 0 0 "Some text" 20]
   [:h1 "Hello from Electric Clojure"]
   [:p "Source code for this page is in "
    [:code "src/electric_start_app/main.cljc"]]
   [:p "Make sure you check the "
    [:a {:href "https://electric.hyperfiddle.net/" :target "_blank"}
     "Electric Tutorial"]]])