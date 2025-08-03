(ns restaurant.with-customer.till.events
  (:require
   [com.pangglow.math :as math]
   [com.pangglow.random :as random]
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [re-frame.core :refer [reg-event-db]]
   [restaurant.events :as r-events]
   [restaurant.with-customer.calc.events :as wc-calc-events]
   [restaurant.with-customer.events :as wc-events]
   [restaurant.with-customer.general.events :as wc-general-events]
   [restaurant.with-customer.paths :as wc-paths]))

(defn save-sale-nop [current-bill]
  (println "Save Sale" {:sale current-bill}))

(defn denomination? [[face-value note? :as x]]
  (and (vector? x) (number? face-value) (boolean? note?)))

(defn till-delta* [st current-till-id denomination delta]
  (utl/assrt (denomination? denomination) ["till-delta" {:denomination denomination}])
  (utl/assrt (random/random-id? current-till-id) ["Expected a :till/id" {:current-till-id current-till-id}])
  (let [denomination-path [:db :till/id current-till-id :till/till (s/keypath denomination)]
        qty (s/select-one denomination-path st)]
    (utl/assrt (number? qty) ["till-delta, qty must be a number" {:qty qty}])
    (utl/assrt (number? delta) ["till-delta, delta must be a number" {:delta delta}])
    (if (nil? qty)
      (if (neg? delta)
        (utl/assrt false ["Can't remove denomination when till doesn't have it"
                          {:denomination denomination :delta delta :qty qty
                           :till (s/select-one [:db :till/id current-till-id :till/till] st)}])
        (s/setval denomination-path delta st))
      (if (neg? (+ qty delta))
        (utl/assrt false ["Can't remove from till more than have" {:denomination denomination :delta delta :qty qty}])
        (s/transform denomination-path (partial + delta) st)))))

(defn apply-till-deltas* [current-till-id denominations-m st]
  (let [res (reduce (fn [st [denomination delta]]
                      (till-delta* st current-till-id denomination delta))
              st
              denominations-m)]
    (utl/nothing false "apply-till-deltas" {:denominations-m denominations-m
                                            :current-till-id current-till-id
                                            #_#_:old-till (s/select [:db :till/id current-till-id :till/till] res)})
    res))

(comment
  (let [{:keys [current-till-id] :as st} (wc-events/new-demo-till* {})
        till1 (s/select-one [:db :till/id current-till-id] st)
        st0 (apply-till-deltas* current-till-id {[1000 true] 2} st)
        till2 (s/select-one [:db :till/id current-till-id] st0)]
    [till1 till2]))

(defn check-out* [st
                  device
                  {:bill/keys [id given-back-m] :as current-bill}
                  current-till-id]
  (utl/assrt (simple-keyword? device) ["In check-out, device required (translation change)" {:device device}])
  (save-sale-nop current-bill)
  (let [st0 (->> st
              ;; We've given money back to the customer, so it has to be taken out of the till
              (apply-till-deltas* current-till-id (update-vals given-back-m math/negate))
              (s/setval (wc-paths/bill-calc-state id) :show-recorded)
              #_(r-events/set-session-event :session-event.type/check-out current-bill)
              (#(r-events/inject-many* % [:session-event-data :session-event-type]
                  [current-bill
                   :session-event.type/check-out])))]
    (if (= :phone device)
      (wc-general-events/select-phone-tab* st0 :bill "check-out-button")
      st0)))

(reg-event-db
  ::check-out
  (fn [st [_ device current-bill current-till-id]]
    (check-out* st device current-bill current-till-id)))

(defn delta-denomination-rf [delta-f]
  (fn [current-m denomination]
    (let [res (->> current-m
                (s/transform [(s/keypath denomination)]
                  (fn [qty] ((fnil delta-f 0) qty)))
                (remove (comp zero? val))
                (into {}))]
      (utl/nothing false "delta-denomination-rf" {:current-m current-m :denomination denomination :res res})
      res)))

(defn till-delta [st current-till-id denomination delta]
  (utl/assrt (denomination? denomination) ["till-delta" {:denomination denomination}])
  (utl/assrt (random/random-id? current-till-id) ["Expected a :till/id" {:current-till-id current-till-id}])
  (let [denomination-path [:db :till/id current-till-id :till/till (s/keypath denomination)]
        qty (s/select-one denomination-path st)]
    (utl/assrt (number? qty) ["till-delta, qty must be a number" {:qty qty}])
    (utl/assrt (number? delta) ["till-delta, delta must be a number" {:delta delta}])
    (if (nil? qty)
      (if (neg? delta)
        (utl/assrt false ["Can't remove denomination when till doesn't have it"
                          {:denomination denomination :delta delta :qty qty
                           :till (s/select-one [:db :till/id current-till-id :till/till] st)}])
        (s/setval denomination-path delta st))
      (if (neg? (+ qty delta))
        (utl/assrt false ["Can't remove from till more than have" {:denomination denomination :delta delta :qty qty}])
        (s/transform denomination-path (partial + delta) st)))))

(defn receive-note* [st current-bill-id current-till calc-state note-denomination customer-change-amount config dec-from-giving-change?]
  (utl/assrt (boolean? dec-from-giving-change?) ["want boolean" {:dec-from-giving-change? dec-from-giving-change?}])
  (utl/assrt (number? (first note-denomination)) ["receive-note, expected a number" {:note-denomination note-denomination
                                                                                     :type-first-note-denomination (type (first note-denomination))}])
  (let [{:till/keys [id till]} current-till]
    (utl/nothing false "receive-note" {:note-denomination note-denomination :calc-state calc-state})
    (case calc-state
      :giving-change (let [delta-denomination-f (delta-denomination-rf (if dec-from-giving-change? dec inc))]
                       (->> st
                         (s/transform (wc-paths/customer-change current-bill-id) #(delta-denomination-f % note-denomination))
                         ;; This will happen when check out
                         #_(#(till-delta % id note-denomination (if dec-from-giving-change? 1 -1)))))
      :billing-out (let [increase-denomination-f (delta-denomination-rf inc)
                         st0 (->> st
                               (s/transform (wc-paths/customer-receipts current-bill-id) #(increase-denomination-f % note-denomination))
                               (#(till-delta % id note-denomination 1)))
                         now-amt (+ customer-change-amount (first note-denomination))]
                     (utl/nothing false "Is amt > needed?" {:customer-change-amount now-amt :b? (pos? now-amt)})
                     (if (and (-> config :auto-give-change :b?) (pos? now-amt))
                       (wc-calc-events/give-change* st0 current-bill-id now-amt till)
                       st0)))))

(comment
  (let [st {}
        current-bill-id 1
        current-till-id 1
        calc-state :view
        n [100 true]]
    (receive-note* st current-bill-id current-till-id calc-state n 100 {} false)))

(reg-event-db
  ::receive-note
  (fn [st [_ current-bill-id current-till calc-state note-denomination customer-change-amount config dec-from-giving-change?]]
    (receive-note* st current-bill-id current-till calc-state note-denomination customer-change-amount config dec-from-giving-change?)))

