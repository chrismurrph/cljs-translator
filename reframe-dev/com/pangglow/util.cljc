(ns com.pangglow.util
  (:refer-clojure :exclude [ident?])
  (:require
   #?(:clj [clojure.java.io :as java-io])
   [clojure.edn :as edn]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [com.pangglow.random :as random]
   [com.rpl.specter :as s])
  #?(:clj (:import [java.util Base64])))

(defn view-map [m]
  (into {} m))

(defn obfuscate [value]
  #?(:clj (-> (Base64/getEncoder)
            (.encodeToString (.getBytes value "UTF-8")))
     :cljs (comment value)))

(defn deobfuscate [encoded]
  #?(:clj (-> (Base64/getDecoder)
            (.decode encoded)
            (String. "UTF-8"))
     :cljs (comment encoded)))

(comment
  (obfuscate "something")
  (let [word "anything"
        traveller (obfuscate word)]
    [word traveller (deobfuscate traveller)]))

;;
;; If you just used pull [*] then this would be called ref->eid, but we don't know how it was pulled
;;
(defn ref->value
  ([m dbg-src]
   (when m
     (assert (map? m) ["-ref values coming from Datomic queries are maps" {:m m :dbg-src dbg-src}])
     (-> m first val)))
  ([m]
    (ref->value m "No dbg-src")))

(comment
  (= 7 (ref->value {:a 7} "")))

;; For use with a many ref
(defn ref->values-set [ref]
  (->> ref
    (map ref->value "ref->values-set")
    (set)))

(defn now []
  #?(:clj (java.util.Date.)
     :cljs "Not yet needed"))

(defn now-ts []
  #?(:clj (.getTime (java.util.Date.))
     :cljs "Not yet needed"))

(defn add-minute [date]
  #?(:clj (-> date
            (.getTime)
            (+ (* 60 1000))
            (java.util.Date.))
     :cljs (comment date)))


(comment
  [(now)
   (add-minute (now))])

(defn minute-sequence [start n]
  #?(:clj (take n (iterate add-minute start))
     :cljs (comment start n)))

;;
;; Order so the later ones are more important. Will return the earlier ones that can be dropped
;; leaving behind only the n we want.
;;
(defn leaving-last-n [n xs]
  (let [excess (- (count xs) n)]
    (take excess xs)))

(comment
  (leaving-last-n 20 [1 2 3 4 5 6]))

(comment
  (minute-sequence (now) 5))
;;
;; These 3 ought to go into contrib/util. But when I put them there get compile warnings
;;

(defn first-item [xs id id-f]
  (let [entity (first xs)]
    (when entity
      (id-f entity))))

(defn next-item [xs id id-f]
  (let [entity (->> (drop-while (comp (partial not= id) id-f) xs)
                 (take 2)
                 second)]
    (when entity
      (id-f entity))))

(defn prev-item [xs id id-f]
  (next-item (reverse xs) id id-f))

(comment
  (let [xs [{:id 10} {:id 11} {:id 12} {:id 13} {:id 14}]
        curr-id 14
        by-id (fn [e] (or (:id e) (:db/id e)))]
    (prev-item xs curr-id by-id)))

(defn remove-vals [pred-f m]
  (reduce (fn [m [k v]]
            (if (pred-f v)
              m
              (assoc m k v)))
    {}
    m))

(comment
  (= {5 7, 6 9} (remove-vals even? {1 2 3 4 5 7 6 9})))

(defn export-records [record->str recording recording-file]
  #?(:clj (->> recording
            (map record->str)
            (str/join "\n")
            (#(str % "\n"))
            (spit recording-file))
     :cljs (comment record->str recording recording-file)))

(defn assrt [pred msg]
  #?(:cljs (when (not pred)
             (println (str "ASSRT Error Msg: <" msg ">"))
             #_(.error js/console msg)
             (js/error msg))
     :clj (assert pred msg)))

(defn assrt-nop [pred msg])

(defn see-assrt [pred msg]
  #?(:cljs (println (str "ASSRT Error Msg: <" msg ">"))
     :clj (assert pred msg)))

;;
;; Weird that this function can't be centralised. That why it is scattered around.
;; If use this one electric says:
;; Cannot read properties of undefined
;; Maybe the problem (V.2 problem) is that this ns has no Electric in it.
;; i've now centralised at wc-ui.
;;
#_(defn pixelate [s]
    (str s "px"))

;;
;; Do nothing, but with some text to help readability of the code.
;; If you really want you can print the output.
;; But the main intention of this fn is not for logging or debugging, but to:
;; 1./ Have arg text (reason) be 'used up', so don't get linter errors
;; 2./ Keep the documentation function of what used to be debug statements
;; 3./ Explain what's going on in logic positions where nothing needs to be done.
;; For example an `if` with nothing rather than a `when` can add clarifying documentation.
;; 4./ You've archived debugging, but still may want to use it again.
;;
;; print? arg comes first as easy to search for `utl/nothing true` and change to false after a debugging session.
;; Before this change there was too much copy and pasting of text seen in output
;;
(defn nothing
  ([print? text m]
   (assrt (boolean? print?) ["First arg needs to be true or false" {:print? print? :text text :m m}])
   (assrt ((complement boolean?) text) "boolean must be first arg not second")
   (assrt ((complement boolean?) m) "boolean must be first arg not third")
   (when print?
     (if (seq m)
       (do
         (println text)
         (pprint/pprint m))
       (if (map? text)
         (pprint/pprint text)
         (println text)))))
  ([print? text]
    (if (boolean? print?)
      (nothing print? text {})
      (if (and (string? print?) (map? text))
        (do
          (println (str "WARN, you forgot T/F as first arg - " print?))
          (nothing true print? text))
        (assrt false ["Wrong args for 2 arg call to nothing" {:print? print? :text text}])))))

;;
;; Use when `(nothing true ` is intended to be permanent
;;
(defn always
  ([text m]
   (nothing true text m))
  ([text]
   (always text {})))

(defn probe-transform [path f st]
  (let [b4 (s/select-one path st)
        res (s/transform path f st)
        after (s/select-one path res)]
    (nothing true "probe-transform" {:path path
                                         :b4 b4
                                         :after after})
    res))

(defn round [n] (int (+ 0.5 n)))

;;
;; We prefer to round, but use this when visible edges don't line up
;;
(def round-nop identity)

(defn fail-chance [percent-fail]
  (let [fail? (<= (round (* 100 (rand))) percent-fail)]
    (nothing false "Perhaps fail" {:fail? fail? :percent-fail percent-fail})
    fail?))

(comment
  (fail-chance 33)
  (count (filterv true? (mapv (fn [_] (fail-chance 60)) (take 100 (range))))))

(defn calc-proportion [low high value-at call-dbg]
  (assrt (<= low value-at high) ["Expected value-at to be higher than or equal to low and lower than or equal to high" {:low low :high high :value-at value-at :call-dbg call-dbg}])
  (let [spread (- high low)
        distance (- value-at low)]
    (/ distance spread)))

(defn calc-middle [low high]
  (assrt (>= high low) ["calc-middle, high s/be >= low" {:low low :high high}])
  (let [half-spread (/ (- high low) 2)]
    (round (+ low half-spread))))

(defn calc-top [low high]
  (assrt (>= high low) ["calc-top, high s/be >= low" {:low low :high high}])
  low)

(comment
  (let [low 11
        high 70
        value-at 13]
    #_(calc-middle low high)
    (calc-proportion low high (+ low (/ (- high low) 2)) "")))

(comment
  (let [low 10
        high 70
        value-at 13]
    (calc-proportion low high value-at "")))

;;
;; Centralise a line within a line. Returns [line-top line-bottom]
;; Find the middle between top-bound and bottom-bound.
;; Find half the length of line-length.
;; Middle minus half the length is line-top
;; Middle plus half the length is line-bottom
;;
(defn centralise-line [top-bound bottom-bound line-length]
  (assrt (> bottom-bound top-bound) ["Bounds dominance problem" {:bottom-bound bottom-bound :top-bound top-bound}])
  (assrt ((some-fn pos? zero?) line-length) ["Bad line length" {:line-length line-length :top-bound top-bound :bottom-bound bottom-bound}])
  (let [middle (calc-middle top-bound bottom-bound)
        half-length (/ line-length 2)
        line-top (round (- middle half-length))
        line-bottom (round (+ middle half-length))]
    (nothing false "centralise-line" {:middle middle :line-length line-length :line-top line-top :line-bottom line-bottom})
    [line-top line-bottom]))

(comment
  (centralise-line 0 300 13))

(defn kw->string [kw]
  (if (string? kw)
    kw
    (do
      (when kw (assrt (keyword? kw) ["kw->string needs a keyword" {:kw kw}]))
      (and kw (subs (str kw) 1)))))

(defn overlap? [left right]
  (let [[left-x _ width] left
        [right-x] right]
    (> (+ left-x width) right-x)))

;; -rf stands for 'returns function'. No longer gonna use -hof
(defn left-pad-rf [with-str max-sz]
  (fn [goes-at-right]
    (assrt (string? goes-at-right) ["Can only left pad a string" {:goes-at-right goes-at-right}])
    (let [diff-count (- max-sz (count goes-at-right))]
      (if (pos? diff-count)
        (str (apply str (take diff-count (repeat with-str))) goes-at-right)
        goes-at-right))))

(def left-pad-spaces (partial left-pad-rf " "))
(def left-pad-2-zeros (left-pad-rf "0" 2))
(def left-pad-3-zeros (left-pad-rf "0" 3))

(comment
  (left-pad-3-zeros "3"))

(defn resolve-resource-path [resource]
  #?(:clj (try (java-io/resource resource) (catch Exception _ nil))
     :cljs (comment resource)))

(defn slurp-resource [resource]
  #?(:clj (some-> resource resolve-resource-path slurp)
     :cljs (comment resource)))

(defn read-resource
  ([resource verbose?]
   (let [contents (slurp-resource resource)]
     (if contents
       (edn/read-string contents)
       (when verbose?
         (println "Resource file missing" {:resource resource})))))
  ([resource]
   (read-resource resource true)))

(defn ident-id? [id]
  (random/random-id? id))

(defn table? [table]
  (let [kw? (qualified-keyword? table)]
    (if kw?
      (let [after-slash (-> (str table)
                          (str/split #"/")
                          second)]
        (= "id" after-slash))
      false)))

(defn ident? [xs]
  (when (and (vector? xs) (>= (count xs) 2))
    (let [[table id] xs]
      (and (table? table) (ident-id? id)))))

(comment
  (table? :table/my)
  (table? :table/id))

(defn non-nil [m]
  (when m
    (->> m
      (keep (fn [[k v]]
              (when (some? v)
                [k v])))
      (into {}))))

(comment
  (non-nil nil))

(defn rm-colon [kw]
  (subs (str kw) 1))

(defn ns-key-rf [b4-slash]
  (let [ns-part (rm-colon b4-slash)]
    (fn [kw]
      (keyword (str ns-part "/" (rm-colon kw))))))

(defn table-ns-keys-rf-1 [table]
  (let [b4-slash (-> (str table)
                   (str/split #"/")
                   first)
        ns-key-f (ns-key-rf b4-slash)]
    (fn [m]
      (s/transform [s/MAP-KEYS] ns-key-f m))))

(defn attrib-key->cardinality [attrib-key]
  (if (-> attrib-key name (str/ends-with? "s-ref")) :many :one))

(defn unwrap-set-vals-of-m [m]
  (->> m
    (s/transform [s/ALL] (fn [[k v :as entry]]
                           (let [cardinality (attrib-key->cardinality k)]
                             (case cardinality
                               :one [k (first v)]
                               entry))))
    (s/transform [s/MAP-VALS] (fn [{:keys [table id] :as v}]
                                (if (set? v)
                                  (s/transform [s/ALL] (fn [{:keys [table id] :as v}]
                                                         (if (and table id)
                                                           [table id]
                                                           v)) v)
                                  (if (and table id)
                                    [table id]
                                    v))))))

(comment
  (let [m #:person{:id #{"LS73TDA4RHWT1Y26"}, :name #{"Alice"}, :age #{25}
                   :somes-ref #{{:table :person/id :id "3"} {:table :person/id :id "4"}}}]
    (pprint/pprint (unwrap-set-vals-of-m m))))

(comment
  (let [m #:person{:id #{"LS73TDA4RHWT1Y26"}, :name #{"Alice"}, :age #{25} :some-ref #{{:table :person/id :id "3"}}}]
    (pprint/pprint (unwrap-set-vals-of-m m))))

(comment
  (let [b4 {:id "LS73TDA4RHWT1Y26", :name "Alice", :age 25}
        table-ns-keys (table-ns-keys-rf-1 :person/id)]
    (table-ns-keys b4)))

(defn transform-map-keys-rf [transforms]
  (fn [m]
    (nothing false "transform-map-keys" {:transforms transforms :m m})
    (s/transform [s/MAP-KEYS (s/pred #(contains? transforms %))] transforms m)))

(comment
  (let [data {:created "2023-01-01"
              :updated "2023-05-15"  ;; This key isn't in the key-map
              :price 19.99}
        transform-map-keys (transform-map-keys-rf {:created :ts/created
                                                   :item/created :ts/created
                                                   :price :product/price
                                                   :item/price :product/price})]
    (transform-map-keys data))
  )

(defn simple->qualified-kw-rf [ns-part]
  (fn [kw]
    (if (qualified-keyword? kw)
      (do
        (assert (= ns-part (namespace kw)) ["Already qualified, but in a different way"])
        kw)
      (keyword (str ns-part "/" (rm-colon kw))))))

(defn table-ns-keys-rf-2 [mk-kw-qualified-f]
  (fn [m]
    (s/transform [s/MAP-KEYS] mk-kw-qualified-f m)))

(defn ns-keys-rf [table]
  (let [b4-slash (-> (str table)
                   (str/split #"/")
                   first)
        ns-part (rm-colon b4-slash)
        mk-kw-qualified (simple->qualified-kw-rf ns-part)]
    (table-ns-keys-rf-2 mk-kw-qualified)))

;;
;; ns keys of an entity in 2 ways:
;; - By ns-ing using the table, so :price becomes :product/price if the table was :product/id
;; - More specialist way according to a simple map (rama->fe-m).
;; Facilitates a codebase that can use Datomic style namespacing even if just stored with simple keys.
;; Datomic style namespacing encourages generic cross-entity keys such as :ts/updated.
;; Obviously rama->fe-m will come from a map of metadata that is indexed by many-table.
;;
(defn transform-entity-rf [many-table rama->fe-m]
  (let [ns-keys (table-ns-keys-rf-1 many-table)
        transform-keys (if rama->fe-m
                         (transform-map-keys-rf rama->fe-m)
                         identity)
        transform-f (comp transform-keys ns-keys)]
    (fn [entity]
      (let [res (transform-f entity)]
        (nothing true "transform-entity" {:in entity :out res})
        res))))

;;
;; In:
;; :person/name
;; Out:
;; :person/id :name
;;
(defn attribute->table+attribute [ns-ed-attribute src-dbg]
  (assrt (qualified-keyword? ns-ed-attribute) ["Not a qualified keyword" {:ns-ed-attribute ns-ed-attribute :src-dbg src-dbg}])
  (let [[table-part-1 attrib-part] (str/split (str ns-ed-attribute) #"/")
        table-part-2 (rm-colon table-part-1)
        table (keyword (str table-part-2 "/id"))
        simple-attribute (keyword attrib-part)]
    [table simple-attribute]))

(comment
  (attribute->table+attribute :person/name ""))

(defn entity? [entity]
  (let [identifiers (->> entity
                      keys
                      (remove #(= % :db/id))
                      (filterv table?))
        id (first identifiers)
        value (get entity id)]
    (and (= 1 (count identifiers)) (ident-id? value))))

(comment
  (let [session {:db/id 17592186045421,
                 :session/id "BWVJD4KKFEZ9M3D4",
                 :session/first-seen #inst "2025-04-02T06:12:38.861-00:00",
                 :session/last-seen #inst "2025-04-04T14:50:11.185-00:00",
                 :session/user-ref #:db{:id 17592186045425},
                 :session/visits-ref [#:db{:id 17592186045423} #:db{:id 17592186045445}]}]
    (entity? session)))

(defn entity->ident [entity dbg-src]
  (if (ident? entity)
    entity
    (do
      (assrt (entity? entity) ["entity->ident needs an entity" {:entity entity :dbg-src dbg-src}])
      (let [identifiers (->> entity
                          keys
                          (remove #(= % :db/id))
                          (filterv table?))
            table (first identifiers)]
        (when (= 1 (count identifiers))
          (let [id (get entity table)]
            (assrt (ident-id? id) ["Could not find an ident from entity" {:entity entity}])
            [table id]))))))

(defn entity->id [{simple-id :id :as entity}]
  (if simple-id
    simple-id
    (let [id (-> entity #(entity->ident % "entity->id") second)]
      (assrt (ident-id? id) ["Not found an id from entity" {:entity entity :id id}])
      id)))

(defn fulcro-db? [db]
  #_(println "db" {:db db})
  (let [entities (s/select [s/MAP-VALS s/MAP-VALS] db)]
    (every? entity? entities)))

(defn table-into-st [table st]
  (assrt (map? st) "table-into-st")
  (let [exists? (map? (s/select-one [:db table] st))]
    (if exists?
      st
      (s/setval [:db table] {} st))))

(comment
  (let [st0 {}
        st1 {:db {:bill/id {}}}]
    (table-into-st :bill/id st1)))

(defn entity-into-st-rf [table]
  (assrt (table? table) ["entity-into-st-rf, table not a table" {:table table}])
  (fn [entity st]
    (assrt (map? st) "entity-into-st-rf, st not a map")
    (assrt (entity? entity) ["Not an entity" {:entity entity :table table}])
    (let [id (get entity table)]
      ;; If there isn't already a {} under table one won't be produced, so we fix that first
      (->> st
        (table-into-st table)
        (s/transform [:db table] #(conj % [id entity]))))))

(comment
  "See what bad job starting with st0 makes, compared to st1. Fix by putting table in if it is not there. Fixed now!"
  (let [st0 {}
        st1 {:db {:bill/id {}}}
        entity-into-st (entity-into-st-rf :bill/id)
        an-entity {:bill/id (random/random-id) :bill/identifier "Hi"}
        res0 (entity-into-st an-entity st0)
        res1 (entity-into-st an-entity st1)]
    [(fulcro-db? (:db res1))
     (fulcro-db? (:db res0))]))

;;
;; By putting st as last arg fits with using Specter which works with thread-last macro
;; This could be improved to have a list of targets as 2nd arg.
;; Then ./src/restaurant/with_customer/mutations/general/inject-bill-lines could be make more concise.
;; Check it out as a template for how to improve this fn.
;;
(defn entities-into-st-rf [table]
  (assrt (table? table) ["entities-into-st-rf, table not a table" {:table table}])
  (let [entity-into-st (entity-into-st-rf table)]
    (fn [entities st]
      (assrt (vector? entities) ["entities-into-st-rf, entities not a vector" {:entities entities}])
      ;; nil being put into Rama when ought to be dissoc using this path:
      ;; [(path/keypath *parent-ref *ref-attribute) path/ALL (path/pred= *ident) r/NONE>]
      ;; When fixed put this back and no need for remove nil? below
      #_(assrt (every? entity? entities) ["entities-into-st-rf: some items not entities" {:table table
                                                                                        :example (some (fn [entity]
                                                                                                         (when-not (entity? entity)
                                                                                                           entity))
                                                                                                   entities)}])
      (reduce
        (fn [st table-entity]
          (entity-into-st table-entity st))
        st
        (remove nil? entities)))))

;;
;; One above puts into existing state.
;; This can be used for that where there's nothing pre-existing.
;;
(defn ->fulcro-format [xs indexing-kw]
  (->> xs
    (mapv (fn [entity]
            (assert (> (-> entity keys count) 1) ["Pointless to put single key entity into fulcro-format" entity])
            [(indexing-kw entity) entity]))
    (into {})))

(comment
  (-> (->fulcro-format [{:a 1 :b "1"} {:a 2 :b "2"} #_{:a 3}] :a)
    #_(get 2)))

(comment
  (let [entity {:table/id "QRI54LWZAZGE6ESH",
                :ts/created 1722501620680,
                :shape/four-points [[179 131] [414 131] [414 218.46429999999998] [179 218.46429999999998]],
                :table/num 1, :bounds {:x 179, :y 131, :width 235, :height 87.46429999999998}}
        db {:wall/id {}, :table/id {"QRI54LWZAZGE6ESH" entity}}]
    (and
      (fulcro-db? db)
      (entity? entity))))

(def not-blank? (every-pred string? seq))

;; pad (comp utl/left-pad-2-zeros str)
;; count-of 10
;; range (range 1 (inc count-of))
;; gen-count-text-f (fn [txt] (mapv #(str txt " " (pad %)) range))

(defn gen-many-text-rf [n]
  (let [pad-f (comp left-pad-2-zeros str)
        range (range 1 (inc n))]
    (fn [txt] (mapv #(str txt " " (pad-f %)) range))))

(comment
  (= [false false false true]
    [(not-blank? nil)
     (not-blank? "")
     (not-blank? [])
     (not-blank? "A")]))

(defn remove-entities-using-idents
  "Using an ident to find the entity that will be removed. Done to get rid of orphans. No real functionality.
   Be sure they don't have an unknown parent before removing them"
  [db idents]
  (when (seq idents)
    (assrt (keyword? (ffirst idents)) ["remove-entities-using-idents, not an ident" {:ident (first idents)}]))
  (reduce
    (fn [st [table id]]
      (nothing false "Permanently removing from st" {:table table :id id})
      (update st table dissoc id))
    db
    idents))

(comment
  (let [st {:shape/id {1 {:shape/id 1}
                       2 {:shape/id 2}}}]
    (remove-entities-using-idents st [[:shape/id 2]])))

;;
;; If you know the idents and the target where they are kept this fn will thoroughly remove them,
;; so remove the underlying entities as well.
;;
(defn remove-idents [idents target db]
  (assrt (= 3 (count target)) ["Inside :db we expect target to be the idents of a particular entity"])
  (let [idents-set (set idents)
        db-1 (update-in db target #(vec (remove idents-set %)))]
    (remove-entities-using-idents db-1 idents)))

(comment
  (let [db {:shape/id {1 {:shape/id 1}
                       2 {:shape/id 2}
                       3 {:shape/id 3}}
            :drawing/id {1 {:drawing/id 1
                            :drawing/shapes [[:shape/id 1] [:shape/id 2] [:shape/id 3]]}}}]
    (remove-idents [[:shape/id 2]] [:drawing/id 1 :drawing/shapes] db)))

;;
;; Exactly the same as assoc used with multiple [k v] args.
;; Helper if want to replace the multiple assoc with Specter. Everything else seems to be able to be replaced
;; 'inline' while still being readable. This codebase is going to have no more assoc nor update nor dissoc. Ha!
;; assoc would be a bit silly with specter b/c st goes last arg yet variable assoc requires variable number of args, unless you
;; wrap them all in a vector. Possible - see setvals->>.
;; :pairs (([:db/id 17592186047032] [:ts/updated #inst "2025-04-29T09:22:12.058-00:00"]))
;;
(defn setvals-> [st & pairs]
  (let [pairs (partition 2 pairs)]
    (assrt (> (count pairs) 1) ["In setvals->, ought to rather use s/setval directly when only one" {:pairs pairs}])
    (reduce
      (fn [st [k v]]
        (s/setval [k] v st))
      st
      pairs)))

(comment
  (setvals-> nil :db/id 10 :ts/updated 20))

(comment
  (let [st {}]
    (setvals-> st :text "Hi" :bullets ["first bullet" "second bullet"])))

;;
;; Compatible with Specter. In neither of these two setvals fns do you need to pair up individual
;; [k v] with vectors - so still being done the `assoc` (varargs) way.
;; (In mutation helper fns we pair them up, so don't get confused).
;;
(defn setvals->> [pairs st]
  (assrt (and (vector? pairs) (-> pairs count even?)) ["Expected pairs" {:pairs pairs}])
  (apply setvals-> st pairs))

(comment
  (let [st {}]
    (setvals->> [:text "Hi" :bullets ["first bullet" "second bullet"]] st)))

(defn parse-int [s]
  #?(:clj (Integer/parseInt s)
     :cljs (js/parseInt s)))

(defn s->integer
  [s]
  (assrt (string? s) ["Check first if string b4 use s->integer" {:s s :type-s (type s)}])
  (when (not-blank? s)
    (-> s str/trim parse-int)))

(defn str->int [s]
  ((fnil int 0) (s->integer s)))

(comment
  (str->int 3)
  (s->integer 3)
  (= 0 (str->int ""))
  (= 3 (str->int "3.6")))

(defn remove-last [s]
  (assrt (string? s) ["remove-last requires a string" {:s s}])
  (subs s 0 (-> s count dec)))

(comment
  (remove-last "hello"))

(defn update-char [s idx new-char]
  (str (subs s 0 idx) new-char (subs s (inc idx))))

(comment
  (= "Hallo" (update-char "Hello" 1 "a")))

(defn remove-least-significant [s]
  (assrt (string? s) ["remove-least-significant requires a string" {:s s}])
  (let [zero-str? #(= "0" (str %))
        shorter (->> (reverse s)
                  (drop-while zero-str?))
        idx (dec (count shorter))
        res (update-char s idx "0")]
    (if (every? zero-str? res)
      "0"
      res)))

(comment
  (let [s "200"]
    (remove-least-significant s)))

;; Find smallest key and dec its value
(let [debug? false]
  (defn remove-least-significant-m [m key-concentrator]
    (nothing debug? "remove-least-significant-m 1" {:in m})
    (let [zero-less-m (remove (comp zero? val) m)
          smallest (apply min (mapv key-concentrator (keys zero-less-m)))
          _ (nothing debug? "remove-least-significant-m 2" {:smallest smallest})
          res (->> zero-less-m
                (keep (fn [[k v]]
                        (if (= smallest (key-concentrator k))
                          (let [new-value (dec v)]
                            (when-not (zero? new-value)
                              [k new-value]))
                          [k v])))
                (into {}))]
      (nothing debug? "remove-least-significant-m 3" {:out res})
      res)))

(comment
  (let [m {[1 false] 0, [100 true] 0, [5 false] 1, [200 true] 1, [1000 true] 0, [10 false] 1, [50 true] 0, [20 true] 0, [500 true] 0}]
    (remove-least-significant-m m first)))

(comment
  ;; Before fix was returning empty
  (let [m #_{[1000 true] 1, [100 true] 1} {[500 true] 1, [200 true] 1}]
    (remove-least-significant-m m first)))

(comment
  (let [m {[1000 true] 2
           [200 true] 3
           [10 false] 4
           [5 false] 5
           [1 false] 2}]
    (remove-least-significant-m m first)))

(defn mk-idx-map [[identifier idx]]
  {:identifier identifier :idx idx})

;;
;; From a seq, create a {} of str -> idx, where idx explicitly indicates the ordinal
;;
(defn order-index [xs]
  (->> xs
    (map-indexed (comp vec reverse vector))
    (into {})))

(defn dims->svg-rect-path [{:keys [x y width height]}]
  (assrt (every? number? [x y width height]) ["dims->svg-rect-path: Must all be numbers" {:x x :y y :width width :height height}])
  (str "M " x " " y " h " width " v " height " h -" width " Z"))

(def opacities (->> (mapcat identity (repeat ["0.30" "0.00" "0.15"]))
                 (take 25)))

;;
;; When the opacities are going to meet again make sure they are different. ie first and last are always different.
;;
(defn n-opacities [n]
  (let [opacities (take n opacities)]
    (if (= (first opacities) (last opacities))
      (let [[one two & others] opacities]
        (into [two one] others))
      opacities)))

(comment
  (n-opacities 4))

(defn partition-equally [n]
  (->> (repeat n (/ 1 n))
    (reductions +)
    (cons 0)
    (partition 2 1)
    (map vec)))

(comment
  (= [[0 1/3] [1/3 2/3] [2/3 1N]] (partition-equally 3)))

;;
;; n is the number of paths wanted. At 1 the whole rect as a path. At 2 will be 2 halves of the rect.
;;
(defn rect->path-dims [{:keys [x width] :as dims} n]
  (assrt (every? number? [x width n]) ["rect->path-dims: Must be numbers" {:x x :width width :n n}])
  (let [each (round-nop (/ width n))]
    (reduce
      (fn [paths n]
        (let [path (dims->svg-rect-path (assoc dims :width each
                                          :x (+ x (* n each))))]
          (conj paths path)))
      []
      (range n))))

;;
;; Would be better if original 3 values were all in seconds.
;;
(defn timeout [reason now-ms timestamp-ms timeout-ms]
  (assrt-nop (every? number? [now-ms timestamp-ms timeout-ms]) ["timeout needs numbers" {:now-ms now-ms :timestamp-ms timestamp-ms :timeout timeout-ms}])
  (let [time-lapsed (int (/ (- now-ms timestamp-ms) 1000))
        timeout (int (/ timeout-ms 1000))
        countdown (- timeout time-lapsed)]
    (nothing false (str "TIMEOUT:" reason)
      {#_#_:now-ms now-ms
       #_#_:timestamp-ms timestamp-ms
       :time-lapsed time-lapsed :timeout timeout :countdown countdown})
    [(>= time-lapsed timeout) countdown]))

(def int-chars #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9})
(defn sliding-str->int [s]
  (->> s
    (drop-while (comp not int-chars))
    (take-while int-chars)
    (apply str)
    (str->int)))

(defn debug-transform [path transform-f st msg]
  (let [current-val (s/select-one path st)
        new-st (s/transform path transform-f st)
        new-val (s/select-one path new-st)]
    (println (str "transfom: " msg) {:path path :original-val current-val :transformed-to new-val})
    new-st))

(defn debug-setval [path new-value st msg]
  (let [current-val (s/select-one path st)
        new-st (s/setval path new-value st)
        new-val (s/select-one path new-st)]
    (println (str "setval: " msg) {:path path :original-val current-val :overwrote-to new-val})
    new-st))

(defn remove-nil-values
  "Remove entries with nil values from a map"
  [m]
  (into {} (remove (fn [[_ v]]
                     (nil? v))
             m)))

(defn de-dup [xs]
  (vec (distinct xs)))

(comment
  (de-dup [1 2 3 3 4 3]))
