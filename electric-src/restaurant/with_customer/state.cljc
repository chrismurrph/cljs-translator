(ns restaurant.with-customer.state
  (:require
   [com.pangglow.query.front-end :as fe-q]
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [restaurant.query.fe :as r-fe-q]
   [restaurant.with-customer.query.fe :as wc-fe-q]
   [restaurant.with-customer.domain :as wc-domain]))

;;
;; :db is fine here.
;; The others ought to be filled in reactively as soon as bill-lines start appearing.
;; Currently it is almost certainly user interaction, which won't work when the current bill is filled in by another user
;; i.e. we are only picking it up via a reactive database.
;;
#_(def !wc (atom {:db {} :line-top 120 :line-bottom 180 :scroll-row-num 1}))
;; The above caused inaccurate rendering so we switched back:
(def !wc (atom {:line-top 0 :line-bottom 0 :db {} :awaiting-user-query? true}))

;;
;; Keep this. Is used for mutations we don't want to record
;;
#_(def wc-mutation (partial swap! !wc))
(defn wc-mutation [f & args]
  (utl/assrt (fn? f) ["Probably not yet converted back from a keyword" {:f f}])
  (apply swap! !wc f args))

(defn wc-mutation-dbg [path f & args]
  (utl/assrt (fn? f) ["Probably not yet converted back from a keyword" {:f f}])
  (println "wc-mutation-dbg" {:path path :b4 (s/select-one path @!wc)})
  (let [st0 (apply swap! !wc f args)
        dbg-res (s/select-one path st0)]
    (println "wc-mutation-dbg" {:path path :b4 (s/select-one path @!wc) :after dbg-res})
    st0))

;;
;; Always as many across as down (just the way happens to be done for now),
;; which is why first 2 are the same.
;; [break-at num-rows width-adjust height-adjust]
;;
(defn break-at [max-visible-squares total]
  (let [last-adjust [5 5 4 4]]
    (cond
      (<= total 4) [2 2 6 7]
      (<= total 9) [3 3 4 5]
      (<= total 16) [4 4 4 5]
      (<= total 25) last-adjust
      :else (do
              (println "WARN (break-at): Gone over max visible so some won't show" {:max-visible-squares max-visible-squares :total total
                                                                                    :wont-show (- total max-visible-squares)})
              last-adjust))))

;;
;; [break-at num-rows width-adjust height-adjust]
;;
(defn break-at-phone [max-visible-squares total]
  (let [last-adjust [3 4 4 4]]
    (cond
      (<= total 8) [2 4 6 5]
      (<= total 12) last-adjust
      :else (do
              (println "WARN (break-at-phone): Gone over max visible so some won't show" {:max-visible-squares max-visible-squares :total total
                                                                                          :wont-show (- total max-visible-squares)})
              last-adjust))))

;;
;; Adjusted means there's a gap for a textbox above
;; [num-cols num-rows width-adjust height-adjust]
;;
(defn break-at-adjusted-phone [max-visible-squares total]
  (let [last-adjust [3 4 5 3]]
    (cond
      (<= total 8) [2 4 6 5]
      (<= total 12) last-adjust
      :else (do
              (println "WARN (break-at-adjusted-phone): Gone over max visible so some won't show" {:max-visible-squares max-visible-squares :total total
                                                                                                   :wont-show (- total max-visible-squares)})
              last-adjust))))

;;
;; The lower the adjustment, the more space being taken up.
;;
(defn break-at->dims [{:keys [device width height] :as opts} [break-at num-rows width-adjust height-adjust]]
  (utl/assrt (map? opts) ["break-at->dims, opts must be map?" {:opts opts}])
  (when (= :desktop device)
    (utl/assrt (= break-at num-rows) ["Before the device was a phone, we assumed to be filling whereby num rows same as num columns"
                                   {:opts opts :break-at break-at :num-rows num-rows}]))
  (let [container-width (or width wc-domain/container-width)
        container-height (or height wc-domain/container-height)
        item-width (int (- (/ container-width break-at) width-adjust))
        item-height (int (- (/ container-height num-rows) height-adjust))]
    [item-width item-height]))

;; We make it a darker blue if it is a heading that when pressed will go to another level (darker can go deeper).
(defn item->colour [{:item/keys [kind]}]
  (if (= :item.kind/heading kind) wc-domain/deep-water-colour wc-domain/shallow-water-colour))

(def bill-identifier->colour (constantly wc-domain/shallow-water-colour))

;;
;; choices are the squares that are seen.
;; as well as id each choice has: top, left, colour
;;
(defn entities->choices-rf [query-f sort-by-attribute entity->colour-f]
  (fn [{:keys [device max-visible-squares width height] :as opts} db heading-id removed-f?]
    (utl/assrt (#{:phone :adjusted-phone :desktop} device) ["Bad device" {:device device}])
    (utl/assrt (int? max-visible-squares) ["Problem with :max-visible-squares" {:opts opts}])
    (utl/assrt (every? int? [width height]) ["width and height must be ints" {:width width :height height}])
    (let [entities (->> (query-f db heading-id removed-f?)
                     (sort-by sort-by-attribute))
          break-at-f (case device
                       :desktop break-at
                       :phone break-at-phone
                       :adjusted-phone break-at-adjusted-phone
                       (println ["No matching device (entities->choices-rf)" {:device device}]))
          [num-across :as break-at] (break-at-f max-visible-squares (count entities))
          [item-width item-height] (break-at->dims opts break-at)
          entity-choices (map-indexed (fn [idx entity]
                                        (let [colour (entity->colour-f entity)
                                              n-across (rem idx num-across)
                                              left (+ 2 (* n-across item-width) (* n-across 2))
                                              n-down (quot idx num-across)
                                              top (+ 2 (* n-down item-height) (* n-down 2))]
                                          (assoc entity :top top :left left :colour colour)))
                           (take max-visible-squares entities))]
      (when (#{:adjusted-phone :phone} device)
        (utl/nothing false "entities->choices-rf" {:max-visible-squares max-visible-squares :device device}))
      {:entity-choices entity-choices
       :item-width item-width
       :item-height item-height
       :break-at break-at})))

;;
;; If there is no heading-id then those not under a heading are shown.
;; Some that are under a heading will be headings themselves.
;; So infinite recursion is supported.
;;
(def order-choices (entities->choices-rf r-fe-q/child-items :item/description item->colour))
(def identifier-choices (entities->choices-rf wc-fe-q/child-bill-identifiers :bill-identifier/name bill-identifier->colour))
(def unpaid-bill-choices (entities->choices-rf wc-fe-q/child-unpaid-bills :bill/name bill-identifier->colour))

;;
;; price * quantity of each bill-line
;;
(defn bill-lines->total-owed [db bill-lines]
  (reduce
    (fn [acc {:bill-line/keys [product quantity] :as bill-line}]
      (let [{:product/keys [price]} (fe-q/ident->entity db product "bill-lines->total-owed")
            extension (* price quantity)]
        (+ extension acc)))
    0
    bill-lines))

;;
;; Simpler than items->order-choices. Here just increase top each line
;;
(defn- display-bill-lines [db bill-lines]
  (let [row-drop 30
        total-top (+ 0 (* row-drop (count bill-lines)))
        total-left 0
        display-lines (vec (map-indexed
                             (fn [idx bill-line]
                               (let [{:bill-line/keys [product quantity]} bill-line
                                     _ (utl/assrt (utl/ident? product) ["The product in an bill-line needs to be an ident" {:bill-line bill-line}])
                                     {:item/keys [description] :product/keys [price]} (fe-q/ident->entity db product "display-bill-lines")
                                     extension (* price quantity)]
                                 (assoc bill-line :top (* row-drop idx) :left 0 :description description :extension extension :quantity quantity)))
                             bill-lines))]
    {:display-lines display-lines :total-top total-top :total-left total-left}))

(defn displayed-bill-lines [db bill-line-idents scroll-row-num]
  (let [high-index (+ scroll-row-num wc-domain/visible-record-count)
        bill-lines (fe-q/idents->entities db bill-line-idents "displayed-bill-lines")
        m (->> bill-lines
            (sort-by :ts/created >)
            (map-indexed (fn [idx bill-line]
                           [(inc idx) bill-line]))
            (filter (fn [[idx]]
                      (and (<= scroll-row-num idx) (< idx high-index))))
            #_((fn [elements]
                 (utl/assrt (<= (count elements) visible-record-count) ["Too many" {:scroll-row-num scroll-row-num :high-index high-index}])
                 elements))
            (mapv second)
            (display-bill-lines db))]
    (assoc m :total-owed (bill-lines->total-owed db bill-lines))))

(comment
  ;; Between 2 and 7 inclusive is [:b :c :d :e :f :g], which is what get here
       [ 1  2  3  4  5  6  7]
  (->> [:a :b :c :d :e :f :g :h :i :j :k :l]
    (map-indexed (fn [idx kw]
                   [(inc idx) kw]))
    (filter (fn [[idx kw]]
              (<= 2 idx 7)))
    (mapv second))
  (<= 1 1 10))

(defn check-bills [bills dbg-src]
  (utl/assrt (vector? bills) ["" {}])
  (let [bad-bill (some (fn [{:bill/keys [id identifier] :as bill}]
                         (when (or (= [] identifier) (nil? identifier) (nil? id))
                           bill))
                   bills)]
    (utl/assrt (nil? bad-bill) ["Bad bill" {:bill bad-bill :dbg-src dbg-src :bills bills}]))
  bills)

(defn check-bills-nop [bills dbg-src]
  bills)

;;
;; We want the most recent to come first, so oldest bills, most likely to be billing out next, are nearest to the right thumb
;;
(defn backgrounded-identifiers [db bill-identifier]
  (utl/assrt ((some-fn string? utl/ident?) bill-identifier) ["Unexpected bill-identifier" {:bill-identifier bill-identifier}])
  (let [bills (fe-q/table-entities db :bill/id)
        unpaid-bills (->> (check-bills-nop bills "bills")
                       (s/select [s/ALL #(not= bill-identifier (:bill/identifier %))])
                       (sort-by :ts/created >)
                       (vec))
        identifiers (->> (check-bills-nop unpaid-bills "unpaid-bills")
                      (s/select [s/ALL :bill/identifier s/NIL->VECTOR]))]
    (utl/nothing false "backgrounded-identifiers 1" {:backgrounded-identifiers identifiers :bill-identifier bill-identifier})
    [unpaid-bills identifiers]))

;;
;; What is kept at :bill/identifier is either a string or an ident
;;
(defn bill-identifer->name [db bill-identifer dbg-src]
  (utl/assrt ((some-fn string? utl/ident?) bill-identifer) ["Unexpected bill-identifier" {:bill-identifier bill-identifer :dbg-src dbg-src}])
  (let [just-string? (string? bill-identifer)]
    (if just-string?
      [true bill-identifer]
      (let [{:bill-identifier/keys [name]} (fe-q/ident->entity db bill-identifer "Finding name for TextContainer")]
        [false name]))))

(defn bill-identifer->name-rf [db dbg-src]
  (fn [identifier]
    (second (bill-identifer->name db identifier dbg-src))))

(defn unpaid-bills->texts [identifier->name unpaid-bills]
  (->> unpaid-bills
    (map (comp identifier->name :bill/identifier))
    (set)))

(def scroll-area-height 300)
(def scroll-rect-width 15)
(def scroll-rect-height (* 3 scroll-rect-width))
(def pixels-per-record (/ scroll-rect-height wc-domain/visible-record-count))

;;
;; What proportion are we from the top, then use it on num-records
;;
(defn record-at [{:keys [line-top line-bottom]} scroll-value-at num-records]
  (utl/assrt num-records ["" {}])
  (let [proportion (utl/calc-proportion line-top line-bottom scroll-value-at "record-at")]
    (utl/round (* proportion num-records))))

;;
;; 45 pixels is 9 records. 5 pixels per record
;; Determines length of the vertical line.
;;
(defn num-records->scroll-length [n]
  (* n pixels-per-record))

(comment
  (num-records->scroll-length 1))

(comment
  (float (/ 255 2))
  (+ 0.5 (/ 255 2))
  (int (+ 0.5 (/ 255 2)))
  (utl/round (/ 255 2))
  (/ 254 2)
  (+ 0.5 (/ 254 2))
  (int (+ 0.5 (/ 254 2)))
  (utl/round (/ 254 2)))

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
