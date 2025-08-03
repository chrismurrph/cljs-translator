(ns restaurant.with-customer.paths)

;;
;; Any time want to search for a path put it here.
;; Putting individual attributes here might well only harm readability.
;; Indirection is not universally useful!
;;

(defn bill-calc-state [bill-id]
  [:db :bill/id bill-id :bill/calc-state])

(defn customer-receipts [bill-id]
  [:db :bill/id bill-id :bill/received-from-customer-m])

(defn customer-change [bill-id]
  [:db :bill/id bill-id :bill/given-back-m])