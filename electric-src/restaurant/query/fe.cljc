(ns restaurant.query.fe
  (:require
   [com.pangglow.query.front-end :as fe-q]
   [com.pangglow.random :as random]
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]))

(defn display-top-level [state]
  (->> state
    (s/setval [:stage :platform] s/NONE)
    (s/setval [:db] s/NONE)
    (s/setval [:config] s/NONE)
    (s/setval [:available-identifiers] s/NONE)))

;;
;; If there is no heading-id then those not under a heading are shown.
;; Some that are under a heading will be headings themselves.
;; So infinite recursion is supported.
;;
(defn children-rf [table parent-attribute]
  (fn [db heading-id removed-f?]
    (utl/assrt (fn? removed-f?) ["Need a removed-f?" {:removed-f? removed-f?}])
    (utl/assrt ((some-fn nil? random/random-id?) heading-id) ["Strange heading-id" {:heading-id heading-id}])
    (let [xs (remove removed-f? (fe-q/table-entities db table))
          filterf (if (nil? heading-id)
                    (comp not parent-attribute)
                    (comp #{heading-id} parent-attribute))]
      (filterv filterf xs))))

(def child-items (children-rf :item/id :item/heading-id))

(comment
  (let [xs [{:remove? true :name "A"} {:remove? false :name "B"}]]
    #_(remove :remove? xs)
    (remove (constantly false) xs)))

;;
;; The topmost heading doesn't have a parent/heading, in which case heading-id returned here will be nil.
;; ie. that's how we know it is the topmost
;;
(defn parent-of-item-heading [db heading-id]
  (let [{:item/keys [heading-id]} (fe-q/ident->entity db [:item/id heading-id] "parent-of-item-heading")]
    heading-id))
