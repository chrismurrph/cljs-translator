(ns reframe-output.views
  (:require [restaurant.events]
            [re-frame.core :refer [dispatch]]))

(defn ->class [_] "")

(defn main-view
  [ring-req]
  (let [error false
        paths nil
        total-size 100
        foregound "#3366CC"
        background "#EEEEEE"]
    [:div {:class (->class :qr/body-7)}
     (if error
       [:div error]
       [:div {:class (->class :qr/login-box-7)}
        [:div "There's supposed to be an SVG here"]])
     [:button
      {:on-click (fn []
                   (re-frame.core/dispatch [:restaurant.events/take-out
                                            [:qr-code]])),
       :class (->class :qr/button-7)} "Done"]]))