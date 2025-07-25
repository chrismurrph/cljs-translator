(ns electric-starter-app.main
  (:require
   [restaurant.ui :as r-ui]
   [restaurant.with-customer.domain :as wc-domain]
   [hyperfiddle.electric3 :as e]
   [hyperfiddle.electric-svg3 :as svg]
   [hyperfiddle.electric-dom3 :as dom]))

(e/defn PaidLabel []
  (let [width wc-domain/phone-width
        half-width (/ width 2)
        height (- wc-domain/phone-height 50)
        half-height (/ height 2)]
    (svg/svg
      (dom/props {:width (str width) :height (str height)})
      (svg/text
        (dom/props {:x "50%"
                    :y "50%"
                    :opacity "35%"
                    :fill "red"
                    :font-size "110"
                    :text-anchor "middle"
                    :alignment-baseline "middle"
                    :transform (str "rotate(-45 "
                                 half-width
                                 " "
                                 half-height
                                 ")")})
        (dom/text "PAID")))))

(def customer-columns-xs [100 70 70])

(defn ->class [_]
  "")

;; From wc-ui/ normally
(defn generate-absolute-style [top left]
  {:position "absolute"
   :top (r-ui/pixelate top)
   :left (r-ui/pixelate left)})

(e/defn LabelAndAmount
  [top left text-of-label amt]
  (dom/span
    (dom/props {:style (generate-absolute-style top left)})
    (dom/div
      (dom/props {:class (->class :gen/row-indent)
                  :style {:display "grid"
                          :grid-template-columns (r-ui/line-columns customer-columns-xs)
                          :padding "5px"}})
      (dom/div
        (dom/props {:class (->class :wc/customer-desc)})
        (dom/text text-of-label))
      (dom/div
        (dom/props {:class [(->class :wc/product-total-extension) (->class :gen/no-select)]})
        (dom/text amt)))))

(e/defn Main [ring-req]
  (e/client
    (binding [dom/node #?(:cljs js/document.body :clj nil)
              e/http-request (e/server ring-req)]
      (dom/div
        (LabelAndAmount 0 0 "Some text" 20)
        (dom/h1
          (dom/text "Hello from Electric Clojure"))
        (dom/p (dom/text "Source code for this page is in ")
          (dom/code (dom/text "src/electric_start_app/main.cljc")))
        (dom/p (dom/text "Make sure you check the ")
          (dom/a (dom/props {:href "https://electric.hyperfiddle.net/" :target "_blank"})
            (dom/text "Electric Tutorial")))))))