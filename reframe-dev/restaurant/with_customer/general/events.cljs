(ns restaurant.with-customer.general.events
  (:require
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]))

(defn select-phone-tab* [st tab dbg-src]
  (utl/assrt (#{:items :bill :bills :calc} tab) ["Wrong phone tab possibility" {:tab tab}])
  (utl/nothing false "select-phone-tab" {:tab tab :dbg-src dbg-src})
  (->> st
    (s/setval [:selected-tab] tab)))