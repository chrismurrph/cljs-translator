(ns restaurant.with-customer.calc.events
  (:require
   [com.pangglow.query.front-end :as fe-q]
   [com.pangglow.random :as random]
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [re-frame.core :refer [reg-event-db]]
   [restaurant.with-customer.domain :as wc-domain]
   [restaurant.with-customer.events :as wc-events]
   [restaurant.with-customer.general.events :as wc-general-events]
   [restaurant.with-customer.paths :as wc-paths]
   [restaurant.with-customer.till.ui :as wc-till-ui]))

(defn x-digit-1* [st current-bill-id calc-state]
  (let [attr (wc-domain/calc-state->attribute calc-state)]
    (utl/assrt attr ["No attr" {:calc-state calc-state :x-states (keys wc-domain/calc-state->attribute)}])
    (let [attr-value (s/select-one [:db :bill/id current-bill-id attr] st)]
      (utl/nothing true "attr-value" {:attr attr :attr-value attr-value})
      (if (empty? attr-value)
        (let [prev-calc-state (wc-domain/previous-calc-states calc-state)]
          (utl/nothing true "previous-calc-state" {:calc-state calc-state :prev-calc-state prev-calc-state})
          (->> st
            (s/setval (wc-paths/bill-calc-state current-bill-id) prev-calc-state)
            (#(if (= :view prev-calc-state)
                (wc-general-events/select-phone-tab* % :bill "x-digit-2")
                %))))
        (s/transform [:db :bill/id current-bill-id attr] #(utl/remove-least-significant-m % first) st)))))

(reg-event-db
  ::x-digit-1
  (fn [st [_ current-bill-id calc-state]]
    (x-digit-1* st current-bill-id calc-state)))

(defn clear-calc* [current-bill-id st]
  (utl/assrt (map? st) ["clear-calc, st is last arg" {:st st}])
  (if current-bill-id
    (do
      (utl/assrt (random/random-id? current-bill-id) ["clear-calc, current-bill-id is first arg" {:current-bill-id current-bill-id}])
      (->> st
        (s/setval [:db :bill/id current-bill-id :bill/given-back-m] {})
        (s/setval [:db :bill/id current-bill-id :bill/received-from-customer-m] {})
        (s/setval (wc-paths/bill-calc-state current-bill-id) :view)))
    st))

(defn bill-out* [st current-bill-id]
  (utl/nothing false "bill-out-1" {:current-bill-id current-bill-id})
  (->> (wc-events/item-home-1* st current-bill-id)
    (s/setval [:db :bill/id current-bill-id :bill/received-from-customer-m] {})
    (s/setval (wc-paths/bill-calc-state current-bill-id) :billing-out)))

(reg-event-db
  ::bill-out-1
  (fn [st [_ device current-bill-id tab dbg-src]]
    (utl/assrt (simple-keyword? device) ["In bill-out-1, device required (translation change)" {:device device}])
    (let [st0 (bill-out* st current-bill-id)]
      (if (= :phone device)
        (wc-general-events/select-phone-tab* st0 tab dbg-src)
        st0))))

(defn give-change* [st current-bill-id customer-overpayment till]
  (utl/assrt (map? till) ["S/not be an entity" {:till till}])
  (let [new-till (wc-till-ui/take-from-till till customer-overpayment)
        suggested-change (wc-till-ui/subtract till new-till)
        lacking (- customer-overpayment (wc-till-ui/m->amt suggested-change "give-change"))
        auto-change (if (zero? lacking)
                      suggested-change
                      {})]
    (utl/nothing false "give-change" {:lacking lacking :auto-change auto-change :current-bill-id current-bill-id})
    (->> st
      (s/setval [:db :bill/id current-bill-id :bill/given-back-m] auto-change)
      (s/setval (wc-paths/bill-calc-state current-bill-id) :giving-change))))

(reg-event-db
  ::give-change
  (fn [st [_ disabled? current-bill-id customer-overpayment till]]
    (utl/assrt (boolean? disabled?) ["In give-change, disabled? not boolean" {:disabled? disabled?}])
    (if-not disabled?
      (give-change* st current-bill-id customer-overpayment till)
      st)))

;;
;; For the moment we are deleting the bill and all the bill lines.
;; If app customer wants sales info kept then we will still do this,
;; but also save the paid bill on the server, as already done when checked out.
;; We will only do this here delete when we've confirmed that it is there on the server with a query.
;; We will have to set some state: awaiting-confirmation-bill-id. We query that this bill-id really is on the
;; server before deleting its local version.
;; Integrating saving to localstorage with what electric does (waits till Internet connection)
;; will be good too, although conflicting. And my more manual way better, although will need localstorage too.
;; So try to save and if get an error put it in localstorage. When e/InternetUp (or whatever it is called)
;; transitions false -> true then go thru them and repeat the save operation for each.
;;
(defn customer-has-verified* [st current-bill ms]
  (let [{:keys [db] :as st0} (wc-events/remove-current-bill* st current-bill)
        backgrounded-bills (fe-q/table-entities db :bill/id)]
    (if (empty? backgrounded-bills)
      (wc-events/new-bill* st0 ms false)
      (let [{:bill/keys [id] :as bill} (apply min-key :ts/created backgrounded-bills)]
        ;; Switch bill to longest lasting one, mostly likely to be coming seeking to pay
        (utl/nothing false "Switching to oldest backgrounded bill" {:bill bill})
        (s/setval [:current-bill-id] id st0)))))

(reg-event-db
  ::customer-has-verified
  (fn [st [_ current-bill ms]]
    (customer-has-verified* st current-bill ms)))
