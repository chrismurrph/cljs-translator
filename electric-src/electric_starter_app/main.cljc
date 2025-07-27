(ns electric-starter-app.main
  (:require
   [restaurant.mutations :as r-muts]
   [restaurant.with-customer.state :as wc-state]
   [hyperfiddle.electric3 :as e]
   [hyperfiddle.electric-dom3 :as dom]))

(defn ->class [_]
  "")

(e/defn Main [ring-req]
  (e/client
    (binding [dom/node #?(:cljs js/document.body :clj nil)
              e/http-request (e/server ring-req)]
      (let [error false
            paths nil
            total-size 100
            foregound "#3366CC" #_"black"
            background "#EEEEEE" #_"transparent"]
        (dom/div
          (dom/props {:class (->class :qr/body-7)})
          (if error
            (dom/div
              (dom/text error))
            (dom/div
              (dom/props {:class (->class :qr/login-box-7)})
              (dom/div
                (dom/text "There's supposed to be an SVG here"))
              #_(svg/svg
                (dom/props {:width (str total-size) :height (str total-size) :viewBox (str "0 0 " total-size " " total-size)})
                (svg/rect
                  (dom/props {:width total-size :height total-size :fill background}))
                (svg/path
                  (dom/props {:d paths :fill foregound})))))
          (dom/button
            (dom/props {:class (->class :qr/button-7)})
            (dom/On "click" #(wc-state/wc-mutation r-muts/take-out [:qr-code]) nil)
            (dom/text "Done")))))))