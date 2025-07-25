(ns reframe-output.views (:require [r-ui :as r-ui]))

(def customer-columns-xs [100 70 70])

(defn ->class [_]
  "")

(defn generate-absolute-style [top left]
  {:top (r-ui/pixelate top),
 :position "absolute",
 :left (r-ui/pixelate left)})

(defn label-and-amount-view [top left text-of-label amt]
  [:span
 {:style (generate-absolute-style top left)}
 [:div
  {:style
   {:padding "5px",
    :display "grid",
    :grid-template-columns (r-ui/line-columns customer-columns-xs)},
   :class (->class :gen/row-indent)}
  [:div {:class (->class :wc/customer-desc)} text-of-label]
  [:div
   {:class
    [(->class :wc/product-total-extension) (->class :gen/no-select)]}
   amt]]])

(defn main-view [ring-req]
  [:div
 [label-and-amount-view 0 0 "Some text" 20]
 [:h1 "Hello from Electric Clojure"]
 [:p
  "Source code for this page is in "
  [:code "src/electric_start_app/main.cljc"]]
 [:p
  "Make sure you check the "
  [:a
   {:target "_blank", :href "https://electric.hyperfiddle.net/"}
   "Electric Tutorial"]]])

