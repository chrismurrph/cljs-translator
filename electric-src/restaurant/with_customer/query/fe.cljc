(ns restaurant.with-customer.query.fe
  (:require
   [com.pangglow.query.front-end :as fe-q]
   [com.rpl.specter :as s]
   [restaurant.query.fe :as r-fe-q]))

(defn display-bills [state]
  [(s/select [:db :bill/id s/MAP-VALS] state)
   (s/select [:db :bill-line/id s/MAP-VALS] state)])

(defn display-till [current-till-id state]
  (let [path [:db :till/id current-till-id :till/till]]
    (s/select-one path state)))

(defn bills [state]
  (s/select-one [:db :bill/id] state))

(defn bill-lines [state]
  (s/select-one [:db :bill-line/id] state))

(defn display-available-identifiers [state]
  (->> state
    (s/select-one [:available-identifiers])))

(def child-bill-identifiers (r-fe-q/children-rf :bill-identifier/id :bill-identifier/heading-id))
(def child-unpaid-bills (r-fe-q/children-rf :bill/id :bill/heading-id))

(defn parent-of-bill-heading [db heading-id]
  (let [{:bill/keys [heading-id]} (fe-q/ident->entity db [:bill/id heading-id] "parent-of-bill-heading")]
    heading-id))

(defn parent-of-bill-identifier-heading [db heading-id]
  (let [{:bill-identifier/keys [heading-id]}
        (fe-q/ident->entity db [:bill-identifier/id heading-id] "parent-of-bill-identifier-heading")]
    heading-id))

