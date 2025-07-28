(ns electric-starter-app.main
  (:require
   [hyperfiddle.electric-svg3 :as svg]
   [restaurant.with-customer.mutations.till :as wc-muts-till]
   [com.pangglow.util :as utl]
   [hyperfiddle.electric-dom3 :as dom]
   [hyperfiddle.electric3 :as e]
   [restaurant.mutations :as r-muts]
   [restaurant.ui :as r-ui]
   [restaurant.with-customer.ui :as wc-ui]
   [restaurant.with-customer.state :as wc-state]))

(defn ->class [_]
  "")

;; There will be a special rule for these ones to turn it into a simple button
(e/defn contrib-ui-Button [event-f css-kw bill-denomination]
  )

(e/defn OverlayAsPaths [z-index n dims]
  (when (> n 1)
    (dom/div
      (dom/props {:style {:pointer-events "none"
                          :position "absolute"
                          :z-index z-index}})
      (svg/svg
        (e/for [[opacity rect-path] (e/diff-by identity (map vector utl/opacities (utl/rect->path-dims dims n)))]
          (svg/path
            (dom/props {:opacity opacity
                        :d rect-path})))))))

(e/defn MainAsButton [event-f css-kw bill-denomination disabled?]
  (contrib-ui-Button event-f
    {:disabled disabled?
     :style {:position "absolute"}
     :class (->class css-kw)}
    (str (first bill-denomination))))

(defn no-pieces-left [calc-state num-till-pieces]
  (and (= :giving-change calc-state) (not (pos? num-till-pieces))))

(defn would-be-too-much-change [calc-state customer-change-amount denomination-value]
  (when (= :giving-change calc-state)
    (utl/nothing false "would-be-too-much-change" {:customer-change-amount customer-change-amount :denomination-value denomination-value}))
  (and (= :giving-change calc-state) (neg? (- customer-change-amount denomination-value))))

(defn denomination-event [str-kind denomination disabled? amount current-bill-id current-till calc-state selected? config]
  (when-not disabled?
    (let [negate? (and (= :giving-change calc-state) selected? (zero? amount))]
      (when (= :giving-change calc-state)
        (utl/nothing false (str "negate? in " str-kind) {:negate? negate? :selected? selected? :amount amount}))
      (wc-state/wc-mutation wc-muts-till/receive-note current-bill-id current-till calc-state denomination amount config negate?))))

;;
;; Lacking is positive and pressing would make it negative. We never want to give the customer back more than
;; we owe him in change. That might be necessary when down to the last few pieces in the till - enhancement for later.
;; This is when giving-change and selected.
;; With the mentioned contra enhancement it would be enabled if all lower denom pieces were out. And here the button s/be red.
;;
(defn lacking-+ive-would-ive [calc-state selected? face-value lacking]
  (and (= :giving-change calc-state) selected?
    (pos? lacking)
    (neg? (- lacking face-value))))

(e/defn BillOutBankNotes
  [top left current-bill-id current-till calc-state bill-denominations till-denominations
   note-values customer-change-amount config]
  (e/client
    (dom/span
      (dom/props {:style (wc-ui/generate-absolute-style top left)})
      (dom/div
        (dom/props {:class (->class :gen/row-indent)
                    :style {:display "grid"
                            :grid-template-columns (r-ui/line-columns (repeat (count note-values) 110))
                            :padding "5px"}})
        (e/for [bill-denomination (e/diff-by identity note-values)]
          (let [bill-denomination-count (or (get bill-denominations bill-denomination) 0)
                selected? (pos? bill-denomination-count)
                num-till-notes (get till-denominations bill-denomination)
                face-value (first bill-denomination)
                disabled? (or (no-pieces-left calc-state num-till-notes)
                            (lacking-+ive-would-ive calc-state selected? face-value customer-change-amount)
                            (and (not selected?)
                              (would-be-too-much-change calc-state customer-change-amount face-value)))
                event-f (fn [_] (denomination-event "Note" bill-denomination disabled? customer-change-amount current-bill-id
                                  current-till calc-state selected? config))
                css-kw (if (pos? bill-denomination-count) :wc/note-button-selected :wc/note-button)
                trial-and-error-height 32
                width-as-css 100
                dims {:width width-as-css :x 0 :y 0 :height trial-and-error-height}]
            (utl/assrt (number? bill-denomination-count)
              ["No count of bill denomination" {:bill-denominations bill-denominations :bill-denomination bill-denomination}])
            (utl/nothing false "disabled note calc" {:disabled? disabled? :calc-state calc-state :bill-denomination bill-denomination
                                                     :count bill-denomination-count})
            (dom/div
              (dom/props {:style {:position "relative"}})
              (MainAsButton event-f css-kw bill-denomination disabled?)
              (OverlayAsPaths 10 bill-denomination-count dims))))))))

(e/defn Main [ring-req]
  (e/client
    (binding [dom/node #?(:cljs js/document.body :clj nil)
              e/http-request (e/server ring-req)]
      (let [error false
            paths nil
            total-size 100
            foregound "#3366CC" #_"black"
            background "#EEEEEE" #_"transparent"]
        (dom/div
          (dom/props {:class (->class :qr/body-7)})
          (if error
            (dom/div
              (dom/text error))
            (dom/div
              (dom/props {:class (->class :qr/login-box-7)})
              (dom/div
                (dom/text "There's supposed to be an SVG here"))
              #_(svg/svg
                (dom/props {:width (str total-size) :height (str total-size) :viewBox (str "0 0 " total-size " " total-size)})
                (svg/rect
                  (dom/props {:width total-size :height total-size :fill background}))
                (svg/path
                  (dom/props {:d paths :fill foregound})))))
          (dom/button
            (dom/props {:class (->class :qr/button-7)})
            (dom/On "click" #(wc-state/wc-mutation r-muts/take-out [:qr-code]) nil)
            (dom/text "Done")))))))