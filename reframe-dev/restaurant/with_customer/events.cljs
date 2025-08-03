(ns restaurant.with-customer.events
  (:require
   [ajax.edn :refer [edn-request-format edn-response-format]]
   [com.pangglow.js-interop :as js-interop]
   [com.pangglow.query.front-end :as fe-q]
   [com.pangglow.random :as random]
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [re-frame.core :refer [reg-event-db reg-event-fx]]
   [restaurant.domain :as r-domain]
   [restaurant.events :as r-events]
   [restaurant.query.fe :as r-fe-q]
   [restaurant.with-customer.domain :as wc-domain]
   [restaurant.with-customer.general.events :as wc-general-events]
   [restaurant.with-customer.till.ui :as wc-till-ui]
   [restaurant.with-customer.timed.events :as wc-timed-events]))

#_(def port 3000)
#_(def origin "http://localhost")

(def scroll-area-height 300)
(def scroll-rect-width 15)
(def scroll-rect-height (* 3 scroll-rect-width))
(def pixels-per-record (/ scroll-rect-height wc-domain/visible-record-count))

;;
;; 45 pixels is 9 records. 5 pixels per record
;; Determines length of the vertical line.
;;
(defn num-records->scroll-length [n]
  (* n pixels-per-record))

;;
;; Called whenever a record is added or removed. Returns new values for line-top and line-bottom.
;; The mutation that calls this will adjust value-at - the change of new line-top from old line-top will be applied to it.
;;
(defn re-dimension [num-records]
  (utl/assrt (number? num-records) ["" {}])
  (let [length (num-records->scroll-length num-records)]
    (utl/centralise-line 0 scroll-area-height length)))

(comment
  (re-dimension 9))

;; "http://localhost:3000/api/products"
;; Don't need if same place as where served from
#_(defn form-uri [uri]
    (assert (str/starts-with? uri "/"))
    (str origin ":" port uri))

(defn products-fetch-m [org-id]
  {:method          :get
   :uri             (str "/api/products/" org-id)
   :format          (edn-request-format)
   :response-format (edn-response-format)
   :on-success      [::fetch-products-success]
   :on-failure      [::fetch-products-failure]})

(defn insecure-fetch-m [session-id]
  {:method          :get
   :uri             (str "/api/insecure/" session-id)
   :format          (edn-request-format)
   :response-format (edn-response-format)
   :on-success      [::fetch-insecure-success]
   :on-failure      [::fetch-insecure-failure]})

(defn config-fetch-m []
  {:method          :get
   :uri             "/api/config"
   :format          (edn-request-format)
   :response-format (edn-response-format)
   :on-success      [::fetch-config-success]
   :on-failure      [::fetch-config-failure]})

(reg-event-fx
  ::fetch-products
  (fn [{:keys [db]} [_ org-id]]
    {:http-xhrio (products-fetch-m org-id)}))

(reg-event-db
  ::fetch-insecure-success
  (fn [st [_ {:keys [user session]}]]
    (->> st
      (s/setval [:user] user)
      (s/setval [:session] session))))

(reg-event-db
  ::fetch-insecure-failure
  (fn [st [_ error]]
    (js/console.error "Failed to fetch insecure:" error)
    (s/setval [:insecure-error] error st)))

(reg-event-db
  ::fetch-products-success
  (fn [st [_ items-1]]
    (let [table :item/id
          ns-keys (utl/ns-keys-rf table)
          transform-keys (utl/transform-map-keys-rf r-domain/item-rama->fe)
          items-into-st (utl/entities-into-st-rf table)]
      (->> st
        (items-into-st (mapv (comp transform-keys ns-keys) items-1))
        (s/setval [:loaded?] true)))))

(reg-event-db
  ::fetch-products-failure
  (fn [st [_ error]]
    (js/console.error "Failed to fetch products:" error)
    (s/setval [:products-error] error st)))

(reg-event-db
  ::fetch-config-success
  (fn [st [_ config]]
    (s/setval [:config] config st)))

(reg-event-db
  ::fetch-config-failure
  (fn [st [_ error]]
    (js/console.error "Failed to fetch config:" error)
    (s/setval [:config-error] error st)))

(def next-page
  {:items :bill
   :bill :bills
   :bills :calc
   :calc :items})

(reg-event-db
  ::next-page
  (fn [st _]
    (s/transform [:selected-tab] next-page st)))

;;
;; :bill/ui-heading-id is ui only. It would be global if we didn't have many bills that we can switch between. And when switch
;; back we still want to see where was navigating at.
;; heading-id is about an internal relationship between the :item/id, whereby an item can have a parent, via the attribute:
;; :item/heading-id.
;; It is used so that the next item/product the user is picking can be under a heading that's under a heading etc.
;; A similar hierarchy exists with table :bill-identifier/id having :bill-identifier/heading-id.
;;
(defn set-item-heading-in-bill*
  ([st current-bill-id heading-id]
   (->> st
     (s/setval [:db :bill/id current-bill-id :bill/ui-heading-id] heading-id)
     (s/setval [:db :bill/id current-bill-id :bill/ui-parent-id] s/NONE)))
  ([st current-bill-id heading-id parent-id]
   (->> (set-item-heading-in-bill* st current-bill-id heading-id)
     (s/setval [:db :bill/id current-bill-id :bill/ui-parent-id] parent-id))))

(defn set-bill-identifier-heading* [st heading-id]
  (s/setval [:identifier-heading-id] heading-id st))

(defn item-home-1* [st current-bill-id]
  (set-item-heading-in-bill* st current-bill-id s/NONE))

(defn mk-bill [ms first-identifier]
  (let [new-id (random/random-id)]
    {:bill/id new-id :ts/created ms :bill/bill-lines []
     :bill/calc-state :view
     :bill/received-from-customer-m {}
     :bill/given-back-m {}
     :bill/identifier (or first-identifier new-id)}))

(defn see-all-unpaids-toggle* [{:keys [see-all-unpaids?] :as st}]
  (->> st
    (s/setval [:see-all-unpaids?] (not see-all-unpaids?))))

(reg-event-db
  ::select-phone-tab
  (fn [st [_ tab dbg-src]]
    (wc-general-events/select-phone-tab* st tab dbg-src)))

(defn insert-inbetween-by-idx [xs identifier idx]
  (utl/assrt (string? identifier) ["identifier s/be a string" {:identifier identifier}])
  (utl/assrt (number? idx) ["" {}])
  (let [insert {:identifier identifier :idx idx}
        b4-idx (filterv (comp (partial > idx) :idx) xs)
        after-idx (filter (comp (partial < idx) :idx) xs)]
    (reduce into [b4-idx [insert] after-idx])))

;;
;; It is intentional that an identifier that is not one of the wc-domain/default-bill-identifiers-set is
;; silently ignored. Only put back identifiers that are part of this set!
;;
(defn put-back-identifier-3 [identifier available-identifiers]
  (utl/assrt (vector? available-identifiers) ["" {}])
  (when (seq available-identifiers)
    (utl/assrt (-> available-identifiers first string?)
      ["available-identifiers must be strings" {:available-identifiers available-identifiers}]))
  (if (and (string? identifier) (wc-domain/default-bill-identifiers-set identifier))
    (let [xs (mapv (comp utl/mk-idx-map (juxt identity wc-domain/identifier->idx)) available-identifiers)
          idx (wc-domain/identifier->idx identifier)]
      (->> (insert-inbetween-by-idx xs identifier idx)
        (mapv :identifier)))
    available-identifiers))

(defn remove-current-bill* [st current-bill]
  (let [{:bill/keys [id identifier bill-lines]} current-bill]
    (utl/assrt id ["No current-bill" {:current-bill current-bill}])
    (->> st
      (s/transform [:available-identifiers] (partial put-back-identifier-3 identifier))
      (s/transform [:db] (partial utl/remove-idents bill-lines [:bill/id id :bill/bill-lines]))
      (s/setval [:db :bill/id id] s/NONE))))

;;
;; Intended to be FE only, so each client has the same.
;; Might change the wording of the identifiers from a config setting.
;;
(defn inject-default-identifiers* [st]
  (s/setval [:available-identifiers] wc-domain/default-bill-identifiers st))

(reg-event-db
  ::inject-default-identifiers
  (fn [st _]
    (inject-default-identifiers* st)))

;;
;; As there is one user there s/be one thing being edited. But it can be anywhere. Find it and do its mutation to stop the editing.
;; (wc-state/wc-mutation wc-muts-bill/touch-bill-line-toggle current-bill-id id "DesktopDisplayBillLineRecord" at-point)
;;
(defn timer-stopped-editing [{:keys [db current-bill-id] :as st}]
  (let [{:bill/keys [bill-lines]} (fe-q/ident->entity db [:bill/id current-bill-id])
        bill-lines (fe-q/idents->entities db bill-lines "User doesn't need to touch closed anymore")
        pred (fn [{:bill-line/keys [ui-touched?]}] ui-touched?)
        {:bill-line/keys [id] :as editing-bill-line} (first (s/select [s/ALL (s/subselect pred) s/FIRST] bill-lines))]
    (utl/nothing false "Auto closing" {:editing-bill-line editing-bill-line})
    (wc-timed-events/touch-bill-line-toggle* st current-bill-id id "timer-stopped-editing" nil)))

(defn timer-edit-off* [st]
  (utl/nothing false "timer-edit-off")
  (->> st
    (timer-stopped-editing)
    (s/setval [:timer-edit-ms] s/NONE)))

(defn timer-edit-on* [st ms dbg-src]
  (utl/nothing false "timer-edit-on" {:version (r-domain/version) :dbg-src dbg-src})
  (utl/assrt ms ["timer-edit-on needs ms" {:ms ms}])
  (s/setval [:timer-edit-ms] ms st))

(defn new-bill* [st ms manual?]
  (let [first-identifier (s/select-one [:available-identifiers s/FIRST] st)
        {new-id :bill/id :as new-bill} (mk-bill ms first-identifier)
        st0 (->> st
              #_(clear-current-bill)
              ;; Remove the first identifier that is now being used as is in new-bill
              (s/transform [:available-identifiers seq] #(subvec % 1))
              (s/setval [:current-bill-id] new-id)
              (s/setval [:db :bill/id new-id] new-bill))
        st1 (if manual?
              (r-events/inject-many* st0 [:session-event-data :session-event-type] [new-bill :session-event.type/manual-new-order])
              st0)]
    (utl/nothing false "new-bill" {:first-identifier first-identifier :new-bill new-bill})
    (wc-general-events/select-phone-tab* st1 :items "Straight to menu for new customer")))

(reg-event-fx
  ::new-bill
  (fn [{:keys [db]} [_ ms manual?]]
    (let [new-bill-st (new-bill* db ms manual?)]
      {:db new-bill-st})))

(reg-event-db
  ::item-drill-up
  (fn [st [_ current-bill-id heading-id]]
    (let [parent (r-fe-q/parent-of-item-heading (:db st) heading-id)]
      (set-item-heading-in-bill* st current-bill-id (or parent s/NONE)))))

(reg-event-db
  ::item-drill-down
  (fn [st [_ current-bill-id item-id]]
    (let [parent (r-fe-q/parent-of-item-heading (:db st) item-id)]
      (set-item-heading-in-bill* st current-bill-id item-id parent))))

(reg-event-db
  ::nothing
  (fn [st [_ text]]
    (utl/always ["Nothing event" {:text text}])
    st))

(defn pathname->uri [pathname]
  (case pathname
    "/pos.html" "/pos"
    "/pos_demo.html" "/pos-demo"
    pathname))

(defn mk-till [till]
  (let [new-id (random/random-id)]
    {:till/id new-id :till/till till}))

(defn new-demo-till* [st]
  (let [{new-id :till/id :as new-till} (mk-till (wc-till-ui/create-demo-till r-domain/denominations 10))]
    (->> st
      (s/setval [:current-till-id] new-id)
      (s/setval [:db :till/id new-id] new-till))))

(reg-event-fx
  ::initialize-db
  (fn [{:keys [db]} [_ org-id]]
    #_(.addEventListener js/document "contextmenu"
      (fn [e]
        (.preventDefault e)
        false))
    (let [session-id (js-interop/find-cookie r-domain/cookie-name)
          platform-info (js-interop/platform-info)
          pathname (js-interop/pathname)
          uri (pathname->uri pathname)
          db0 (-> db
                (inject-default-identifiers*)
                (new-bill* (js/Date.now) false)
                (new-demo-till*))
          db1 (->> db0
                (s/setval [:platform] platform-info)
                (s/setval [:session-event-data] {:platform-info platform-info
                                                 :app-version (r-domain/version)
                                                 :app-name :pos})
                (s/setval [:session-event-type] :session-event.type/entry)
                (s/setval [:selected-tab] :items)
                (s/setval [:session-id] session-id)
                (s/setval [:uri] uri))]
      {:db db1
       :http-xhrio [(insecure-fetch-m session-id)
                    (config-fetch-m)
                    (products-fetch-m org-id)]})))
