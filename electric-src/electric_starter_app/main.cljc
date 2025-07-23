(ns electric-starter-app.main
  (:require
   [restaurant.with-customer.domain :as wc-domain]
   #_[hyperfiddle.electric-client3 :as e]
   [hyperfiddle.electric3 :as e]
   [hyperfiddle.electric-svg3 :as svg]
   [hyperfiddle.electric-dom3 :as dom]))

#_(e/defn PaidLabel []
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

;; Saving this file will automatically recompile and update in your browser

#_(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (dom/h1 (dom/text "Hello from Electric Clojure"))
      (dom/p (dom/text "Source code for this page is in ")
        (dom/code (dom/text "src/electric_start_app/main.cljc")))
      (dom/p (dom/text "Make sure you check the ")
        #_(PaidLabel)
        (dom/a (dom/props {:href "https://electric.hyperfiddle.net/" :target "_blank"})
          (dom/text "Electric Tutorial"))))))

(e/defn Nothing [app-name ring-req tab-id config]
  (dom/div
    (dom/div
      (dom/text "app-name: " app-name))
    (dom/div
      (dom/text "tab-id: " tab-id))
    (dom/div
      (dom/text "config: " config))))

(e/defn Main [ring-req]
  (e/client
    (binding [dom/node #?(:cljs js/document.body :clj nil)
              e/http-request (e/server ring-req)]
      (set! (.-title js/document) "Pangglow")
      (Nothing "Hi" ring-req 3 {:a 4}))))
