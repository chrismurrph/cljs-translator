(ns restaurant.events
  (:require
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [re-frame.core :refer [reg-event-db]]))

(defn inject* [st path value]
  (utl/assrt (vector? path) ["Always need to inject a path (path is a vector)" {:path path}])
  (->> st
    (s/setval path value)))

(defn take-out* [st path]
  (utl/assrt (vector? path) ["Always need to take out a path (path is a vector)" {:path path}])
  (utl/nothing false "Taking out (from reactive loop)" {:path path})
  (s/setval path s/NONE st))

(reg-event-db
  ::take-out
  (fn [db [_ path]]
    (take-out* db path)))

;;
;; When doing many they must be top level keys
;;
(defn inject-many* [st keywords values]
  (utl/assrt (every? vector? [keywords values]) ["vectors required" {:keywords keywords :values values}])
  (reduce (fn [st [kw val]]
            (s/setval [kw] val st))
    st
    (map vector keywords values)))

(defn take-out-many* [st keywords]
  (utl/assrt (every? keyword? keywords) ["vector of keywords required" {:keywords keywords}])
  (reduce (fn [st kw]
            (s/setval [kw] s/NONE st))
    st
    keywords))


(reg-event-db
  ::inject
  (fn [db [_ path value]]
    (inject* db path value)))

(reg-event-db
  ::inject-true
  (fn [db [_ path]]
    (inject* db path true)))

(reg-event-db
  ::inject-false
  (fn [db [_ path]]
    (inject* db path true)))

(reg-event-db
  ::inject-many
  (fn [db [_ keywords values]]
    (inject-many* db keywords values)))