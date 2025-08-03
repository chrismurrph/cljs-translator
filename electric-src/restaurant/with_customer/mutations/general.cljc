(ns restaurant.with-customer.mutations.general
  (:require
   [clojure.string :as str]
   [com.pangglow.query.front-end :as fe-q]
   [com.pangglow.random :as random]
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [restaurant.with-customer.demo-data :as wc-demo-data]
   [restaurant.domain :as r-domain]
   [restaurant.mutations :as r-muts]
   [restaurant.query.fe :as r-fe-q]
   [restaurant.with-customer.domain :as wc-domain]
   [restaurant.with-customer.mutations.timed :as wc-muts-timed]
   [restaurant.with-customer.state :as wc-state]))

(defn start-from-scratch [st]
  (->> st
    (s/setval [:available-identifiers] s/NONE)
    (s/setval [:current-bill-id] s/NONE)
    (s/setval [:db] {})
    #_(s/setval [:heading-id] s/NONE)
    #_(s/setval [:received-from-customer] s/NONE)))

;;
;; Intended to be FE only, so each client has the same.
;; Might change the wording of the identifiers from a config setting.
;;
(defn inject-default-identifiers [st]
  (s/setval [:available-identifiers] wc-domain/default-bill-identifiers st))

;;
;; Static and very small, but each client has different. To be retrieved on app start up (might not need app state for this, lets test).
;;
(defn inject-bill-identifiers [{:keys [db] :as st} ms]
  (let [bill-identifiers-into-st (utl/entities-into-st-rf :bill-identifier/id)
        bill-identifiers (wc-demo-data/example-bill-identifiers ms)]
    (bill-identifiers-into-st bill-identifiers st)))

;;
;; Just for debugging. In reality users enter and always stays on FE.
;;
(defn inject-unpaid-bills [{:keys [db] :as st} ms]
  (let [bills-into-st (utl/entities-into-st-rf :bill/id)
        bill-lines-into-st (utl/entities-into-st-rf :bill-line/id)
        {heading-id :item/id} (fe-q/triple->entity db [:item/id :item/description "A La Carte"])
        {product-id :item/id} (fe-q/triple->entity db [:item/id :item/description "Bacon Bits"])
        [example-bills example-bill-lines] (wc-domain/example-bills heading-id product-id ms)]
    (->> st
      (bill-lines-into-st example-bill-lines)
      (bills-into-st example-bills))))

(defn description->product-id [db description]
  (let [{product-id :item/id} (fe-q/triple->entity db [:item/id :item/description description])]
    product-id))

;;
;; Inject bill lines into current bill. Helps for testing the scroll control
;; The items are already there.
;; 10 bill lines need to be created and each given one of the 10 items we have queried.
;;
(defn inject-bill-lines [{:keys [db] :as st} ms]
  (let [current-bill-id (s/select-one [:current-bill-id] st)
        {:bill/keys [bill-lines]} (fe-q/ident->entity db [:bill/id current-bill-id])]
    (utl/assrt current-bill-id "Need current bill to put lines into")
    (utl/assrt (empty? bill-lines) ["Already have bill lines" {:current-bill-id current-bill-id}])
    (let [bill-lines-into-st (utl/entities-into-st-rf :bill-line/id)
          description->product-id (partial description->product-id db)
          mk-product-bill-line (partial wc-domain/mk-product-bill-line ms)
          bill-lines (mapv (comp mk-product-bill-line description->product-id)
                       (vector "Choco Oat Cookies" #_(rand-nth wc-demo-data/example-items)))
          bill-line-idents (fe-q/entities->idents :bill-line/id bill-lines)]
      (->> st
        (bill-lines-into-st bill-lines)
        (s/setval [:db :bill/id current-bill-id :bill/bill-lines] bill-line-idents)))))

(defn inject-bill-lines-nop [{:keys [db] :as st} ms]
  st)

(defn init-recording [st]
  (s/setval [:recording] [] st))

;;
;; This is a server side mutation so s/go in a separate ns
;;
#_(defn save-recording [{:keys [recording] :as st}]
    (println "RECORDING")
    (println recording)
    st)

(comment
  (let [st {}]
    (inject-unpaid-bills st 1)))

(defn mk-heading [letter]
  (utl/assrt (string? letter) ["Heading must be a string" {:letter letter}])
  {:bill-identifier/id (random/random-id)
   :bill-identifier/kind :bill-identifier.kind/heading
   :bill-identifier/name letter})

(defn alphabetify-bill-identifiers [st max-visible-squares]
  (let [table :bill-identifier/id
        assoc-actual (fn [st table id heading-id]
                       (s/transform [:db table id] #(assoc %
                                                      :bill-identifier/kind :bill-identifier.kind/actual
                                                      :bill-identifier/heading-id heading-id)
                         st))]
    (r-muts/alphabetify st table :bill-identifier/name mk-heading assoc-actual 3 max-visible-squares)))

(defn un-alphabetify-bill-identifiers [st]
  (let [table :bill-identifier/id
        kind-attribute :bill-identifier/kind
        heading-attribute :bill-identifier/heading-id
        heading-kind-attribute-value :bill-identifier.kind/heading]
    (r-muts/un-alphabetify st table kind-attribute heading-attribute heading-kind-attribute-value)))

;;
;; :bill/ui-heading-id is ui only. It would be global if we didn't have many bills that we can switch between. And when switch
;; back we still want to see where was navigating at.
;; heading-id is about an internal relationship between the :item/id, whereby an item can have a parent, via the attribute:
;; :item/heading-id.
;; It is used so that the next item/product the user is picking can be under a heading that's under a heading etc.
;; A similar hierarchy exists with table :bill-identifier/id having :bill-identifier/heading-id.
;;
(defn set-item-heading-in-bill
  ([st current-bill-id heading-id]
   (->> st
     (s/setval [:db :bill/id current-bill-id :bill/ui-heading-id] heading-id)
     (s/setval [:db :bill/id current-bill-id :bill/ui-parent-id] s/NONE)))
  ([st current-bill-id heading-id parent-id]
   (->> (set-item-heading-in-bill st current-bill-id heading-id)
     (s/setval [:db :bill/id current-bill-id :bill/ui-parent-id] parent-id))))

(defn item-drill-up [{:keys [db] :as st} current-bill-id heading-id]
  (let [parent (r-fe-q/parent-of-item-heading db heading-id)]
    (set-item-heading-in-bill st current-bill-id (or parent s/NONE))))

(defn item-home-1 [st current-bill-id]
  (set-item-heading-in-bill st current-bill-id s/NONE))

(defn item-home-2 [st current-bill-id]
  st)

(defn nothing [st text]
  (println "wc mutation not yet written" {:text text})
  st)

(defn see-all-unpaids-toggle [{:keys [see-all-unpaids?] :as st}]
  (->> st
    (s/setval [:see-all-unpaids?] (not see-all-unpaids?))))

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

(comment
  (let [available-identifiers-1 ["Blue" "Violet"]
        available-identifiers-2 [#_"Yellow" "Green" "Indigo" "Violet"]]
    (put-back-identifier-3 "Blue" available-identifiers-2)))

;;
;; Clearing for UI purposes.
;; Perhaps the bill hasn't been paid yet, so will need to be kept to be brought up again.
;;
#_(defn clear-current-bill [st]
    (->> st
      (s/setval [:current-bill-id] s/NONE)
      (s/setval [:heading-id] s/NONE)
      (s/setval [:billing-out?] s/NONE)
      (s/setval [:received-from-customer] s/NONE)))

#_(defn receive-digit [st current-bill-id n]
  (utl/assrt (number? n) ["receive-digit, expected a number" {:n n :type-n (type n)}])
  (s/transform [:db :bill/id current-bill-id :bill/received-from-customer] #(str % n) st))

;;
;; At the moment available-identifiers is a static list of colours, but can be whatever.
;; An ordered pool of ways to automatically (no user interaction) identify customers.
;; When the identifier has been used it is available for use again.
;;
(defn put-back-identifier-1 [identifier available-identifiers]
  (utl/assrt (vector? available-identifiers) ["" {}])
  (conj available-identifiers identifier))

(defn put-back-identifier-2 [identifier available-identifiers]
  (utl/assrt (vector? available-identifiers) ["" {}])
  (let [xs (mapv (comp utl/mk-idx-map (juxt identity wc-domain/identifier->idx)) available-identifiers)
        idx (wc-domain/identifier->idx identifier)]
    (println "put-back-identifier-2" {:idx idx :xs xs})
    {:res (s/select-one (s/filterer #(> (:idx %) idx)) xs)}))

(comment
  (let [available-identifiers-1 ["Blue" "Violet"]
        available-identifiers-2 [#_"Yellow" "Green" "Indigo" "Violet"]]
    (put-back-identifier-2 "Blue" available-identifiers-2)))

(comment
  (s/setval [(s/subselect s/ALL (s/pred #(> (:idx %) 3))) s/BEFORE-ELEM] {:identifier "Blue", :idx 3}
    [{:identifier "Green", :idx 2} {:identifier "Indigo", :idx 4} {:identifier "Violet", :idx 5}]))

(comment
  (s/setval [s/ALL (s/keypath #(> (:idx %) 3)) s/FIRST] {:identifier "Blue", :idx 3}
    [{:identifier "Green", :idx 2} {:identifier "Indigo", :idx 4} {:identifier "Violet", :idx 5}]))

(comment
  (s/setval [(s/filterer #(< (:idx %) 3)) s/BEFORE-ELEM] {:identifier "Blue", :idx 3}
    [{:identifier "Green", :idx 2} {:identifier "Indigo", :idx 4} {:identifier "Violet", :idx 5}]))

(comment
  (let [xs (map (partial * 1.1) (range 100))
        pred #(> % 19)]
    ;; Doesn't work
    #_(first (s/select [s/ALL (s/subselect pred) (s/subselect first)] xs))
    (= 19.8
      (first (filter pred xs))
      (first (s/select [s/ALL (s/subselect pred) s/FIRST] xs)))))

(defn select-phone-tab [st tab dbg-src]
  (utl/assrt (#{:items :bill :bills :calc} tab) ["Wrong phone tab possibility" {:tab tab}])
  (utl/nothing true "select-phone-tab" {:tab tab :dbg-src dbg-src})
  (->> st
    (s/setval [:selected-tab] tab)))

(defn clear-stage [st]
  (s/setval [:stage] nil st))

(defn timer-error-on [st ms dbg-src]
  (utl/nothing false "timer-error-on" {:version (r-domain/version) :dbg-src dbg-src})
  (utl/assrt ms ["timer-error-on needs ms" {:ms ms}])
  (s/setval [:timer-error-ms] ms st))

(defn timer-error-on-nop [st ms dbg-src]
  (utl/nothing false "timer-error-on-nop" {:version (r-domain/version) :dbg-src dbg-src})
  st)

(defn timer-error-off [st]
  (utl/nothing false "timer-error-off")
  (s/setval [:timer-error-ms] s/NONE st))

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
    (wc-muts-timed/touch-bill-line-toggle st current-bill-id id "timer-stopped-editing" nil)))

(defn timer-edit-off [st]
  (utl/nothing false "timer-edit-off")
  (->> st
    (timer-stopped-editing)
    (s/setval [:timer-edit-ms] s/NONE)))

(defn timer-edit-on [st ms dbg-src]
  (utl/nothing false "timer-edit-on" {:version (r-domain/version) :dbg-src dbg-src})
  (utl/assrt ms ["timer-edit-on needs ms" {:ms ms}])
  (s/setval [:timer-edit-ms] ms st))

(defn set-bill-identifier-heading [st heading-id]
  (->> st
    (s/setval [:identifier-heading-id] heading-id)))

(defn bill-identifier-home [st]
  (set-bill-identifier-heading st s/NONE))

(defn cancel-update-identifier [st current-bill-id ms dbg-src]
  (utl/assrt ms ["cancel-update-identifier needs ms" {:ms ms}])
  (utl/nothing false "cancel-update-identfier" {:current-bill-id current-bill-id :dbg-src dbg-src})
  (->> (timer-error-on st ms dbg-src)
    (s/setval [:db :bill/id current-bill-id :bill/altering-identifier?] false)
    (bill-identifier-home)
    (clear-stage)))

;;
;; Not always creating a r-domain/mk-bill-identifier, but using the existing one. In this case user could have selected
;; the identifier, so has wasted effort.
;;
(defn complete-update-identifier [{:keys [db] :as st} current-bill-id old-identifier text ms existing-bill-identifier]
  (let [identifier->name (wc-state/bill-identifer->name-rf db "complete-update-identifier")
        session-data {:old-name (identifier->name old-identifier) :new-name text}
        {:bill-identifier/keys [id] :as entity} (or existing-bill-identifier (r-domain/mk-bill-identifier ms :bill-identifier.origin/user-created text))
        _ (utl/assrt (utl/ident-id? id) ["id supposed to be a random-id" {:id id}])
        ident [:bill-identifier/id id]
        st0 (->> st
              (s/transform [:available-identifiers] (partial put-back-identifier-3 old-identifier))
              (s/setval [:db :bill-identifier/id id] entity)
              (s/setval [:db :bill/id current-bill-id :bill/identifier] ident)
              (s/setval [:db :bill/id current-bill-id :bill/altering-identifier?] false)
              (bill-identifier-home)
              (clear-stage))]
    (utl/nothing false "complete-update-identifier" {:old-identifier old-identifier :text text :duplicate-bill-identifier existing-bill-identifier})
    (if (and (nil? existing-bill-identifier) (utl/ident? old-identifier))
      (let [{:bill-identifier/keys [origin id] :as overwriting-user-entity} (fe-q/ident->entity db old-identifier)]
        (if (= :bill-identifier.origin/user-created origin)
          (do
            (utl/nothing false "Get rid of" {:overwriting-user-entity overwriting-user-entity})
            (s/setval [:db :bill-identifier/id id] s/NONE st0))
          (r-muts/inject-many st0 [:session-event-data :session-event-type] [session-data :session-event.type/alter-bill-identifier])))
      (r-muts/inject-many st0 [:session-event-data :session-event-type] [session-data :session-event.type/alter-bill-identifier]))))

;;
;; If user does editing but ends up with the same name then that's pointless editing, so we cancel, thus keeping the
;; current identifier.
;; Another case is that the user updates to a bill-identifier name that an existing unpaid bill current uses. Here we
;; also cancel.
;;
(defn update-identifer [{:keys [db] :as st} text current-bill-id ms unpaid-bills dbg-src]
  (utl/assrt current-bill-id "No current-bill-id in update-identifier")
  (utl/assrt ms ["update-identifier needs ms" {:ms ms}])
  (if (utl/not-blank? text)
    (let [identifier->name (wc-state/bill-identifer->name-rf db "update-identifer")
          {old-identifier :bill/identifier} (fe-q/ident->entity db [:bill/id current-bill-id] "update-identifier")
          old-text (identifier->name old-identifier)]
      (utl/assrt (string? old-text) ["update-identifier, expected old-text to be a string" {:old-text old-text :dbg-src dbg-src}])
      (if (or (= old-text text) (wc-domain/default-bill-identifiers-set text))
        (cancel-update-identifier st current-bill-id ms (str dbg-src ":" "text unchanged or is from known defaults"))
        (let [texts (wc-state/unpaid-bills->texts identifier->name unpaid-bills)]
          (if (texts text)
            (cancel-update-identifier st current-bill-id ms (str dbg-src ":" "text is same as an already unpaid bill"))
            (let [existing-bill-identifier (fe-q/triple->entity db [:bill-identifier/id :bill-identifier/name text])]
              (complete-update-identifier st current-bill-id old-identifier text ms existing-bill-identifier))))))
    (cancel-update-identifier st current-bill-id ms (str dbg-src ":" "text is blank"))))

(comment
  (let [ident [:bill-identifier/id 4]]
    (into [:db] ident)))

;;
;; Bill identifiers are global to an organisation, so not yet contained within anything.
;; They are very dynamic as seen by what is done here.
;; Every time user enters a letter (or backspace or whatever) we:
;; 1 Un-alphabetify
;; 2 Set what identifiers are seen, usually (when 1 or more letters entered) making most invisible
;;   (bill-identifier/removed?)
;; 3 If still (removed? not counted) more than wc-domain/max-visible-squares then alphabetify, again only
;;   looking at bill-identifiers that have not been removed. Headings that are created are all set to be not removed.
;; Note that everything has now been done in app state (the :db key).
;; 4 In Bill remove based on removed? so that filtering is achieved (wc-state/identifier-choices).
;; Elsewhere:
;; Make sure that bill-identifiers are all accessed thru 'props' to get the reactivity and have filtering effort only happen once.
;;
(defn bill-identifiers-respond-to-value [max-visible-squares value st]
  (utl/nothing false "bill-identifiers-respond-to-value" {:bill-identifiers-count (count (s/select [:db :bill-identifier/id s/MAP-VALS] st))})
  (let [st0 (->> st
              (un-alphabetify-bill-identifiers)
              (s/transform [:db :bill-identifier/id s/MAP-VALS] (fn [{:bill-identifier/keys [name] :as entity}]
                                                                  (let [match? (str/starts-with? name value)]
                                                                    (utl/assrt (boolean? match?) ["match? not boolean" {:match? match?}])
                                                                    (assoc entity :bill-identifier/removed? (not match?))))))
        still-visible-bill-identifiers (s/select [:db :bill-identifier/id s/MAP-VALS #(= false (:bill-identifier/removed? %))] st0)
        too-many? (> (count still-visible-bill-identifiers) max-visible-squares)]
    (utl/nothing false "Responding to value" {:still-visible-bill-identifiers (count still-visible-bill-identifiers)
                                               :too-many? too-many?})
    (if too-many?
      (alphabetify-bill-identifiers st0 max-visible-squares)
      st0)))

(defn stage-identifier [st v max-visible-squares]
  (utl/assrt (pos-int? max-visible-squares) ["stage-identifier" {:max-visible-squares max-visible-squares}])
  (let [value (->> (str/split v #"\s+")
                (map str/capitalize)
                (str/join " "))]
    (utl/nothing false "identifier running value" {:value value})
    (->> st
      (s/setval [:stage :identifier-text] value)
      (bill-identifiers-respond-to-value max-visible-squares value)
      (s/setval [:identifier-heading-id] s/NONE))))

(defn altering-identifier [st edit-name current-bill-id max-visible-squares]
  (let [st0 (stage-identifier st edit-name max-visible-squares)]
    (->> st0
      (s/setval [:db :bill/id current-bill-id :bill/altering-identifier?] true))))

(defn mk-bill [ms first-identifier]
  (let [new-id (random/random-id)]
    {:bill/id new-id :ts/created ms :bill/bill-lines []
     :bill/calc-state :view
     :bill/received-from-customer-m {}
     :bill/given-back-m {}
     :bill/identifier (or first-identifier new-id)}))

(defn new-bill [st ms manual?]
  (let [first-identifier (s/select-one [:available-identifiers s/FIRST] st)
        {new-id :bill/id :as new-bill} (mk-bill ms first-identifier)
        st0 (->> st
              #_(clear-current-bill)
              ;; Remove the first identifier that is now being used as is in new-bill
              (s/transform [:available-identifiers seq] #(subvec % 1))
              (s/setval [:current-bill-id] new-id)
              (s/setval [:db :bill/id new-id] new-bill))
        st1 (if manual?
              (r-muts/inject-many st0 [:session-event-data :session-event-type]
                [new-bill
                 :session-event.type/manual-new-order])
              st0)]
    (utl/nothing false "new-bill" {:first-identifier first-identifier :new-bill new-bill})
    (-> st1
      (select-phone-tab :items "Straight to menu for new customer"))))

(comment
  ;; This is not what happens with Specter
  (seq []) ;=> nil
  (subvec [3] 1)
  (let [data-1 {:available-identifiers []}
        data-2 {:available-identifiers [:a :b :c]}]
    ;; Only seq works for both data-1 and data-2 (tried s/ALL)
    (s/transform [:available-identifiers seq] #(subvec % 1) data-2))
  ;; these exacly the same, so why use s/pred (and many related) at all?
  (s/select [s/ALL (s/pred even?)] (range 10))
  (s/select [s/ALL even?] (range 10)))

(defn item-drill-down [{:keys [db] :as st} current-bill-id item-id]
  (let [parent-id (r-fe-q/parent-of-item-heading db item-id)]
    (utl/nothing false "item-drill-down" {:current-bill-id current-bill-id :item-id item-id :parent parent-id})
    (set-item-heading-in-bill st current-bill-id item-id parent-id)))

;;
;; Find the row the user touched on our 'line and rect control'. Then go up half the length of the rect. That's because
;; :scroll-row-num is defined at the very top of the rect, and is used by the UI to position the rect.
;;
(defn set-scroll-value-at [st cy num-records]
  (let [row-num (wc-state/record-at st cy num-records)
        half-rect (int (/ wc-domain/visible-record-count 2))
        new-scroll-row-num (- row-num half-rect)]
    (utl/nothing false "set-scroll-value-at" {:new-scroll-row-num new-scroll-row-num :old-scroll-row-num row-num :cy cy})
    (s/setval [:scroll-row-num] new-scroll-row-num st)))

(defn remove-current-bill [st current-bill]
  (let [{:bill/keys [id identifier bill-lines]} current-bill]
    (utl/assrt id ["No current-bill" {:current-bill current-bill}])
    (->> st
      (s/transform [:available-identifiers] (partial put-back-identifier-3 identifier))
      (s/transform [:db] (partial utl/remove-idents bill-lines [:bill/id id :bill/bill-lines]))
      (s/setval [:db :bill/id id] s/NONE))))
