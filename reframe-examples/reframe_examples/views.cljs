(ns reframe-examples.views
  (:require
   [re-frame.core :refer [dispatch]]
   [restaurant.events :as r-events]))

(defn ->class [_]
  "")

(defn main-view [ring-req]
  (let [error false
        paths nil
        total-size 100
        foregound "#3366CC"
        background "#EEEEEE"]
    [:div
     {:class (->class :qr/body-7)}
     (if error
       [:div
        error]
       [:div
        {:class (->class :qr/login-box-7)}
        [:div
         "There's supposed to be an SVG here"]])
     [:button
      {:on-click #(dispatch [::r-events/take-out [:qr-code]])
       :class (->class :qr/button-7)}
      "Done"]]))

(comment
  ;; Gives error, you can't really write reader syntax
  (= ::r-events/take-out (keyword (str ":" "r-events/take-out"))))