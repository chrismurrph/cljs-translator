(ns reframe-output.views
  (:require [restaurant.with-customer.till.events]
            [r-ui :as r-ui]
            [re-frame.core :refer [dispatch]]
            [utl :as utl]
            [wc-ui :as wc-ui]))

(defn lacking-+ive-would-ive
  [calc-state selected? face-value lacking]
  (and (= :giving-change calc-state)
       selected?
       (pos? lacking)
       (neg? (- lacking face-value))))

(defn denomination-event
  [str-kind denomination disabled? amount current-bill-id current-till
   calc-state selected? config]
  (utl/nothing "denomination-event" {:amount amount})
  (when-not disabled?
    (let [negate? (and (= :giving-change calc-state) selected? (zero? amount))]
      (when (= :giving-change calc-state)
        (utl/nothing true
                     (str "negate? in " str-kind)
                     {:negate? negate?, :selected? selected?, :amount amount}))
      (dispatch [:restaurant.with-customer.till.events/receive-note
                 current-bill-id current-till calc-state denomination amount
                 config negate?]))))

(defn would-be-too-much-change
  [calc-state customer-change-amount denomination-value]
  (when (= :giving-change calc-state)
    (utl/nothing false
                 "would-be-too-much-change"
                 {:customer-change-amount customer-change-amount,
                  :denomination-value denomination-value}))
  (and (= :giving-change calc-state)
       (neg? (- customer-change-amount denomination-value))))

(defn ->class [_] "")

(defn no-pieces-left
  [calc-state num-till-pieces]
  (and (= :giving-change calc-state) (not (pos? num-till-pieces))))

(defn overlay-as-paths
  [z-index n dims]
  (when (> n 1)
    [:div
     {:style {:z-index z-index, :position "absolute", :pointer-events "none"}}
     [:svg
      (for [[opacity rect-path]
            (map vector utl/opacities (utl/rect->path-dims dims n))]
        [:path {:opacity opacity, :d rect-path}])]]))

(defn bill-out-bank-notes
  [top left current-bill-id current-till calc-state bill-denominations
   till-denominations note-values customer-change-amount config]
  [:span {:style (wc-ui/generate-absolute-style top left)}
   [:div
    {:style {:padding "5px",
             :display "grid",
             :grid-template-columns (r-ui/line-columns
                                     (repeat (count note-values) 110))},
     :class (->class :gen/row-indent)}
    (for [bill-denomination note-values]
      (let [bill-denomination-count
            (or (get bill-denominations bill-denomination) 0)
            selected? (pos? bill-denomination-count)
            num-till-notes (get till-denominations bill-denomination)
            face-value (first bill-denomination)
            disabled? (or (no-pieces-left calc-state num-till-notes)
                          (lacking-+ive-would-ive calc-state
                                                  selected?
                                                  face-value
                                                  customer-change-amount)
                          (and (not selected?)
                               (would-be-too-much-change calc-state
                                                         customer-change-amount
                                                         face-value)))
            event-f (fn [_]
                      (denomination-event "Note"
                                          bill-denomination
                                          disabled?
                                          customer-change-amount
                                          current-bill-id
                                          current-till
                                          calc-state
                                          selected?
                                          config))
            css-kw (if (pos? bill-denomination-count)
                     :wc/note-button-selected
                     :wc/note-button)
            trial-and-error-height 32
            width-as-css 100
            dims
            {:y 0, :width width-as-css, :x 0, :height trial-and-error-height}]
        (utl/assrt (number? bill-denomination-count)
                   ["No count of bill denomination"
                    {:bill-denomination bill-denomination,
                     :bill-denominations bill-denominations}])
        (utl/nothing false
                     "disabled note calc"
                     {:calc-state calc-state,
                      :bill-denomination bill-denomination,
                      :count bill-denomination-count,
                      :disabled? disabled?})
        [:div {:style {:position "relative"}}
         [:button
          {:disabled disabled?,
           :on-click event-f,
           :style {:position "absolute"},
           :class (->class css-kw)} (str face-value)]
         [overlay-as-paths 10 bill-denomination-count dims]]))]])

(def config
  {:cut-deletes
   {:b? true,
    :desc
    "If user Cuts an item, even a heading, then after a timeout with no paste, that item/heading will be auto-deleted"},
   :items-disappear {:b? false,
                     :desc "Item selection disappears when billing-out?"},
   :scroll-always
   {:b? false,
    :desc
    "When true then the scroll control will always be visible. When false only when needed."},
   :auto-give-change
   {:b? false,
    :desc
    "When have received at least bill amount then automatically go :billing-out -> :giving-change"},
   :git {:sha "3db0fe3", :branch "pos4"},
   :back-position
   {:key :bottom,
    :keys #{:bottom :top :lower-bottom},
    :desc
    "This is for Back and Home pill looking buttons when choosing dishes/products, where they are placed"},
   :prod {:b? false,
          :desc
          "Running on a server on the cloud, used by end users not developers"},
   :app-name
   {:key :pos,
    :keys #{:pos :chat :prods},
    :desc
    "We have many applications that run in docker files. This says which will be run and built as an uberjar."},
   :restaurant {:b? true},
   :record
   {:b? false,
    :desc
    "Record all the wc mutations, that pressing a Developer button can save them to a file on the server"}})

(defn main-view
  [ring-req]
  (let [top 70
        left 0
        current-bill-id "Z2DNP5ZLZ9HTUAH0"
        current-till #:till{:till {[10 false] 10,
                                   [1000 true] 10,
                                   [20 false] 10,
                                   [20 true] 10,
                                   [1 false] 10,
                                   [100 true] 10,
                                   [5 false] 10,
                                   [200 true] 10,
                                   [500 true] 10,
                                   [50 true] 10},
                            :id "G36MR7DXKQ7QDR0C"}
        calc-state :billing-out
        bill-denominations {}
        till-denominations {[10 false] 10,
                            [1000 true] 10,
                            [20 false] 10,
                            [20 true] 10,
                            [1 false] 10,
                            [100 true] 10,
                            [5 false] 10,
                            [200 true] 10,
                            [500 true] 10,
                            [50 true] 10}
        note-values [[100 true] [50 true] [20 true]]
        customer-change-amount -235]
    (utl/nothing true
                 "bill-out-bank-notes"
                 {:till-denominations till-denominations,
                  :note-values note-values,
                  :config config,
                  :top top,
                  :customer-change-amount customer-change-amount,
                  :calc-state calc-state,
                  :current-bill-id current-bill-id,
                  :bill-denominations bill-denominations,
                  :left left,
                  :current-till current-till})
    [bill-out-bank-notes top left current-bill-id current-till calc-state
     bill-denominations till-denominations note-values customer-change-amount
     config]))