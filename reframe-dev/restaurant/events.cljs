(ns restaurant.events
  (:require
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [re-frame.core :refer [reg-event-db]]))

(defn take-out* [st path]
  (utl/assrt (vector? path) ["Always need to take out a path (path is a vector)" {:path path}])
  (utl/nothing true "Taking out (from reactive loop)" {:path path})
  (s/setval path s/NONE st))

(reg-event-db
  ::take-out
  (fn [db [_ path]]
    (take-out* db path)))
