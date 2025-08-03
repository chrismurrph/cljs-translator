(ns restaurant.with-customer.timed.events
  (:require
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [re-frame.core :refer [reg-event-db]]))

;;
;; A touched bill-line has "Remove" button.
;; If any of the bill-lines are touched then :bill/ui-bill-line-touched? is also set for convenience
;;
(defn touch-bill-line-toggle* [st current-bill-id bill-line-id dbg-src at-point]
  (utl/assrt current-bill-id ["No current-bill-id in touch-bill-line-toggle" {:bill-line-id bill-line-id :dbg-src dbg-src}])
  (utl/nothing false "touch-bill-line-toggle" {:bill-line-id bill-line-id :dbg-src dbg-src :at-point at-point})
  (let [res (if (or at-point (nil? bill-line-id))
              st
              (let [bill-line-path [:db :bill-line/id bill-line-id :bill-line/ui-touched?]
                    bill-path [:db :bill/id current-bill-id :bill/ui-bill-line-touched?]]
                (->> st
                  (s/transform bill-path not)
                  (s/transform bill-line-path not))))]
    res))

(reg-event-db
  ::touch-bill-line-toggle
  (fn [st [_ current-bill-id bill-line-id dbg-src at-point]]
    (touch-bill-line-toggle* st current-bill-id bill-line-id dbg-src at-point)))
