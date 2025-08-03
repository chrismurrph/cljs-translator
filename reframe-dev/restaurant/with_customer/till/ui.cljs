(ns restaurant.with-customer.till.ui
  (:require
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]))

;;
;; Every denomination is [face-value note?]. We need this b/c 20 pesos can either be a coin or a note.
;;

(defn create-demo-till [denominations qty-each]
  (reduce (fn [running-till denomination]
            (s/setval [(s/keypath denomination)] qty-each running-till))
    {}
    denominations))

(comment
  (create-demo-till [[1000 true] [500 true] [20 true]] 5))

;;
;; For any map of denomination->quantity, will calculate the total
;;
(defn m->amt
  ([m call-dbg]
   (utl/assrt (map? m) ["Need map of denomination->quantity" {:m m}])
    (when (seq m)
      (let [first-denom (-> m first key)]
        (assert (every? (comp vector? key) m)
          ["Denomination must now be [face-value note?]" {:first-denom first-denom :call-dbg call-dbg}])))
    (reduce (fn [n [[face-value _] v]]
              (+ n (* face-value v)))
      0
      m))
  ([m]
    (m->amt m nil)))

(comment
  (= 2019 (let [m {[1000 true] 1
                   [500 true] 2
                   [5 false] 3
                   [1 false] 4}]
            (m->amt m ""))))

;;
;; This is 'Lacking'
;;
(defn customer-change-amount [received-from-customer total-owed given-back]
  (utl/assrt (every? number? [received-from-customer total-owed given-back]) "Expected numbers")
  (let [res (- received-from-customer total-owed given-back)]
    (utl/nothing false "Lacking" {:res res :received-from-customer received-from-customer :total-owed total-owed :given-back given-back})
    res))

(defn subtract [till bag]
  (utl/remove-vals zero? (reduce (fn [running-till [[face-value _ :as k] v]]
                                   (let [val-a (get running-till k)]
                                     (utl/nothing false "subtract" {:k k :val-a val-a :v v})
                                     (assoc running-till k (- val-a v))))
                           till
                           bag)))

(comment
  (let [till-a {[100] 7 [1] 7 [50] 3}
        till-b {[100] 2}]
    (sort-by (comp - first key) till-a)
    #_(substract till-a till-b)))

(def dbg-take-from-till? false)

;;
;; We can only give change to the customer if it exists in the till. Create a map of notes and coins it is possible
;; to hand back to the customer to satisfy the amount exactly.
;;
(defn take-from-till [till amount]
  (loop [till till
         amount amount]
    (if (or (zero? amount) (empty? till))
      (do
        (utl/nothing dbg-take-from-till? "take-from-till, zero amount or empty till, nothing left to do" {:till till :amount amount})
        till)
      (let [xs (->> till
                 (filter (comp pos? val))
                 (sort-by (comp - first key)))
            [[face-value note?] qty] (first (filter (fn [[k v]]
                                         (<= (first k) amount))
                                 xs))]
        (if face-value
          (let [amt face-value]
            (utl/nothing dbg-take-from-till? "take-from-till, will subtract from till" {:till till :face-value face-value :amount amount :amt amt})
            (recur
              (subtract till {[face-value note?] 1})
              (- amount amt)))
          (do
            (utl/nothing dbg-take-from-till? "take-from-till, nothing left to do" {:till till :amount amount :qty qty})
            till))))))

;;
;; Total of the change needs to add up to the amount. Otherwise till needs replenishing
;;
(comment
  (let [till {[50 true] 1
              [20 false] 3
              [1 false] 10}
        amount 45
        new-till (take-from-till till amount)
        suggested-change (subtract till new-till)]
    (println "new till" {:new-till new-till
                         :b? (= {[50 true] 1, [20 false] 1, [1 false] 5} new-till)
                         :suggested-change suggested-change
                         :lacking (- amount (m->amt suggested-change ""))})))

;;
;; If lacking is 0 then change can be automatically displayed, as it is known to exist.
;;
(comment
  (let [till {[50 true] 1
              [20 false] 3
              [1 false] 10}
        amount 45
        new-till (take-from-till till amount)
        suggested-change (subtract till new-till)]
    (println "new till" {:new-till new-till
                         :b? (= {[50 true] 1, [20 false] 1, [1 false] 5} new-till)
                         :suggested-change suggested-change
                         :lacking (- amount (m->amt suggested-change ""))})))
