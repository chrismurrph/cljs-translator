(ns restaurant.with-customer.phone
  (:require
   [re-frame.core :refer [clear-subscription-cache! dispatch-sync]]
   [reagent.core :as r]
   [reagent.dom.client :as rdc]
   #_[restaurant.domain :as r-domain]
   #_[restaurant.with-customer.events :as wc-events]
   #_[restaurant.with-customer.views :as wc-views]
   [reframe-examples.views :as views]))

(defonce root-container
  (rdc/create-root (.getElementById js/document "app")))

(defn render-main
  []
  ;; Render the UI into the HTML's <div id="app" /> element
  ;; The view function `todomvc.views/todo-app` is the
  ;; root view for the entire UI.
  (.render root-container (r/as-element [views/main-view])))

(defn init []
  (render-main))

(defn reload! []
  ;; Re-render your app, clear caches, etc.
  (clear-subscription-cache!)
  (init))