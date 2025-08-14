(ns electric-starter-app.main
  (:require
   [com.pangglow.js-interop :as js-interop]
   [com.pangglow.util :as utl]
   [hyperfiddle.electric-dom3 :as dom]
   [hyperfiddle.electric-svg3 :as svg]
   [hyperfiddle.electric3 :as e]
   [restaurant.mutations :as r-muts]
   [restaurant.ui :as r-ui]
   [restaurant.with-customer.mutations.till :as wc-muts-till]
   [restaurant.with-customer.state :as wc-state]
   [restaurant.with-customer.ui :as wc-ui]))

(defn ->class [_]
  "")

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

(defn no-pieces-left [calc-state num-till-pieces]
  (and (= :giving-change calc-state) (not (pos? num-till-pieces))))

(defn would-be-too-much-change [calc-state customer-change-amount denomination-value]
  (when (= :giving-change calc-state)
    (utl/nothing false "would-be-too-much-change" {:customer-change-amount customer-change-amount :denomination-value denomination-value}))
  (and (= :giving-change calc-state) (neg? (- customer-change-amount denomination-value))))

(defn denomination-event [str-kind denomination disabled? amount current-bill-id current-till calc-state selected? config]
  (utl/nothing "denomination-event" {:amount amount})
  (when-not disabled?
    (let [negate? (and (= :giving-change calc-state) selected? (zero? amount))]
      (when (= :giving-change calc-state)
        (utl/nothing true (str "negate? in " str-kind) {:negate? negate? :selected? selected? :amount amount}))
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
              (dom/button
                (dom/props {:disabled disabled?
                            :style {:position "absolute"}
                            :class (->class css-kw)})
                (dom/On "click" event-f nil)
                (dom/text (str face-value)))
              (OverlayAsPaths 10 bill-denomination-count dims))))))))

(def config {:cut-deletes
             {:b? true,
              :desc
              "If user Cuts an item, even a heading, then after a timeout with no paste, that item/heading will be auto-deleted"},
             :items-disappear
             {:b? false, :desc "Item selection disappears when billing-out?"},
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
             :prod
             {:b? false,
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

(def debug? true)

(e/defn LoginScreen [mut-f {:keys [username password organisation]} org?]
  (let [get-values (fn []
                     (let [un-value (js-interop/get-value username)
                           pw-value (js-interop/get-value password)]
                       (cond-> [un-value pw-value]
                         org? (conj (js-interop/get-value organisation)))))
        valid? (fn [values]
                 (every? utl/not-blank? values))
        inject (fn [[un-value pw-value org-value :as values]]
                 (utl/nothing debug? "Sign In!" {:username un-value
                                                 #_#_:password pw-value
                                                 :organisation org-value})
                 (mut-f r-muts/inject-many [:username :password :organisation] values))
        keydown (fn [e]
                  (let [key-pressed (.-key e)]
                    (when (= "Enter" key-pressed)
                      (let [values (get-values)]
                        (when (valid? values)
                          (inject values))))))
        reason "Focusing on username"]
    (utl/nothing debug?  reason)
    (js-interop/focus-on-dom-id-3 username)
    (dom/div
      (dom/props {:class (->class :login/body)})
      (dom/div
        (dom/props {:class (->class :login/login-box)})
        (dom/h1
          (dom/props {:class (->class :login/h1)})
          (dom/text "Register/Login"))
        (dom/div
          (dom/div
            (dom/props {:class (->class :login/input-group)})
            (dom/label {:class (->class :login/label)
                        :for username}
              (dom/text "Username"))
            (dom/input (dom/props {:class (->class :login/input)
                                   :type "text"
                                   :id username
                                   :name "username"})
              (dom/On "keydown" keydown nil)))
          (dom/div
            (dom/props {:class (->class :login/input-group)})
            (dom/label (dom/props {:class (->class :login/label)
                                   :for password})
              (dom/text "Password"))
            (dom/input (dom/props {:class (->class :login/input)
                                   :type "password"
                                   :id password
                                   :name "password"})
              (dom/On "keydown" keydown nil)))
          (when org?
            (dom/div
              (dom/props {:class (->class :login/input-group)})
              (dom/label (dom/props {:class (->class :login/label)
                                     :for organisation})
                (dom/text "Organisation"))
              (dom/input (dom/props {:class (->class :login/input)
                                     :type "text"
                                     :id organisation
                                     :name "organisation"})
                (dom/On "keydown" keydown nil))))
          (dom/button
            (dom/props {:class (->class :login/button)})
            (dom/On "click" #(let [values (get-values)] (inject values)) nil)
            (dom/text "Sign In")))))))

(e/defn Main [ring-req]
  (e/client
    (binding [dom/node #?(:cljs js/document.body :clj nil)
              e/http-request (e/server ring-req)]
      (let [f (constantly true)
            m {:username "Chris" :password "pass" :organisation "Edgewood"}]
        (LoginScreen f m false)))))