(ns restaurant.with-customer.mutations.calc
  (:require
   [com.pangglow.query.front-end :as fe-q]
   [com.pangglow.random :as random]
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [restaurant.with-customer.domain :as wc-domain]
   [restaurant.with-customer.mutations.general :as wc-muts]
   [restaurant.with-customer.paths :as wc-paths]
   [restaurant.with-customer.ui.till :as wc-ui-till]))

;;
;; Is about "X" being pressed many times. Smallest denominations first, until get to {}, then will go back to
;; a previous calc-state and start again. When get to :view there's nothing of interest left, so go the the bill.
;;
(defn x-digit-1 [st current-bill-id calc-state]
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
                (wc-muts/select-phone-tab % :bill "x-digit-2")
                %))))
        (s/transform [:db :bill/id current-bill-id attr] #(utl/remove-least-significant-m % first) st)))))

(defn x-digit-2 [st current-bill-id calc-state]
  (let [attr (wc-domain/calc-state->attribute calc-state)]
    (utl/assrt attr ["No attr" {:calc-state calc-state :x-states (keys wc-domain/calc-state->attribute)}])
    (let [attr-value (s/select-one [:db :bill/id current-bill-id attr] st)]
      (utl/nothing true "attr-value" {:attr attr :attr-value attr-value})
      (if (empty? attr-value)
        (let [prev-calc-state (wc-domain/previous-calc-states calc-state)]
          (utl/nothing true "prev-calc-state" {:prev-calc-state prev-calc-state})
          (->> st
            (s/setval (wc-paths/bill-calc-state current-bill-id) prev-calc-state)
            (s/setval [:selected-tab] :bill)))
        (s/transform [:db :bill/id current-bill-id attr] #(utl/remove-least-significant-m % first) st)))))

(defn bill-out-1 [st current-bill-id]
  (utl/nothing true "bill-out-1" {:current-bill-id current-bill-id})
  (->> (wc-muts/item-home-1 st current-bill-id)
    (s/setval [:db :bill/id current-bill-id :bill/received-from-customer-m] {})
    (s/setval (wc-paths/bill-calc-state current-bill-id) :billing-out)))

(defn bill-out-2 [st current-bill-id]
  (utl/nothing true "bill-out-2" {:current-bill-id current-bill-id})
  (s/setval (wc-paths/bill-calc-state current-bill-id) :billing-out st))

(comment
  (true? (empty? {}))
  (true? (empty? nil)))

(comment
  ;; There are 3 5 pesos, is inc-ed to 4 of them
  ;; Here n is 3
  (= {5 4} (s/transform [5]
             (fn [n] ((fnil inc 0) n))
             {5 3}))
  ;; 5 peso amount doesn't exist but is inc-ed to 1
  (= {5 1} (s/transform [5]
             (fn [n] ((fnil inc 0) n))
             {})))

(defn give-change [st current-bill-id customer-overpayment till]
  (utl/assrt (map? till) ["S/not be an entity" {:till till}])
  (let [new-till (wc-ui-till/take-from-till till customer-overpayment)
        suggested-change (wc-ui-till/subtract till new-till)
        lacking (- customer-overpayment (wc-ui-till/m->amt suggested-change "give-change"))
        auto-change (if (zero? lacking)
                      suggested-change
                      {})]
    (utl/nothing false "give-change" {:lacking lacking :auto-change auto-change :current-bill-id current-bill-id})
    (->> st
      (s/setval [:db :bill/id current-bill-id :bill/given-back-m] auto-change)
      (s/setval (wc-paths/bill-calc-state current-bill-id) :giving-change))))

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
(defn customer-has-verified [st current-bill ms]
  (let [{:keys [db] :as st0} (wc-muts/remove-current-bill st current-bill)
        backgrounded-bills (fe-q/table-entities db :bill/id)]
    (if (empty? backgrounded-bills)
      (wc-muts/new-bill st0 ms false)
      (let [{:bill/keys [id] :as bill} (apply min-key :ts/created backgrounded-bills)]
        ;; Switch bill to longest lasting one, mostly likely to be coming seeking to pay
        (utl/nothing false "Switching to oldest backgrounded bill" {:bill bill})
        (s/setval [:current-bill-id] id st0)))))

(defn clear-calc [current-bill-id st]
  (utl/assrt (map? st) ["clear-calc, st is last arg" {:st st}])
  (if current-bill-id
    (do
      (utl/assrt (random/random-id? current-bill-id) ["clear-calc, current-bill-id is first arg" {:current-bill-id current-bill-id}])
      (->> st
        (s/setval [:db :bill/id current-bill-id :bill/given-back-m] {})
        (s/setval [:db :bill/id current-bill-id :bill/received-from-customer-m] {})
        (s/setval (wc-paths/bill-calc-state current-bill-id) :view)))
    st))
