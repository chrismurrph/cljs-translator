(ns restaurant.mutations
  (:require
   [com.pangglow.query.front-end :as fe-q]
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]
   [restaurant.domain :as r-domain]))

(defn trace-nop [st val]
  st)

;;
;; mutations are not really a contrib thing as they rely on a convention of using
;; particular keywords, so putting within this `restaurant` ns/project is as global
;; as a mutation can get.
;;
(defn show-message
  ([{:keys [text] :as st} msg bullets okay-mut]
   (utl/assrt text ["text key in st s/never be nil" {:st st :msg msg}])
   (utl/assrt msg ["msg st s/never be nil" {:st st :msg msg}])
   (when (seq bullets)
     (utl/assrt (every? string? bullets) ["Every bullet must be a string" {:bullets bullets}]))
   (when (and (not= "" text) (not= "" msg))
     (when (not= text msg)
       ;; the user s/have Ok-ed the previous message
       (println "WARN, Overwriting existing message" {:new-message msg :existing-message text})))
   (cond->> (utl/setvals-> st :text msg :bullets bullets)
     okay-mut (s/setval [:okay-mut] okay-mut)))
  ([st msg]
   (show-message st msg [] nil)))

(defn most-letters-group-by [target-attribute entities need-more-than]
  (loop [input entities
         n 5
         output {}]
    (let [greater-than-length (if (= 1 n) 0 need-more-than)
          successfuls (->> (group-by (comp #(apply str (take n %)) target-attribute) input)
                        (s/select [s/ALL #(> (count (val %)) greater-than-length)])
                        (into {}))
          new-output (merge output successfuls)]
      (if (= 1 n)
        new-output
        (let [transfers (set (s/select [s/MAP-VALS s/ALL] successfuls))
              new-input (remove transfers input)]
          (recur new-input (dec n) new-output))))))

(comment
  (let [in ["a" "as" "asd" "aa" "asdf" "qwer" "qwef"]
        successfuls (->> (group-by #(apply str (take 3 %)) in)
                      (s/select [s/ALL #(> (count (val %)) 1)])
                      (into {}))
        transfers (s/select [s/MAP-VALS s/ALL] successfuls)]
    [successfuls transfers]))

;;
;; Remove all inside table where kind-attribute is heading-kind-attribute-value
;; For what's left we need to do two things:
;; 1 Get rid of the kind-attribute
;; 2 Get rid of the heading-attribute
;;
(defn un-alphabetify [st table kind-attribute heading-attribute heading-kind-attribute-value]
  (->> st
    (s/transform [:db table s/ALL (fn [[id entity]]
                                    (= heading-kind-attribute-value (get entity kind-attribute)))] s/NONE)
    (s/transform [:db table s/MAP-VALS] #(dissoc % kind-attribute heading-attribute))))

(comment
  (let [table :bill-identifier/id
        kind-attribute :bill-identifier/kind
        heading-attribute :bill-identifier/heading-id
        heading-kind-attribute-value :bill-identifier.kind/heading
        st {:db {:bill-identifier/id
                 {"1" {:bill-identifier/id "1" :bill-identifier/name "Adam"
                       :bill-identifier/kind :bill-identifier.kind/actual :bill-identifier/heading-id 2}
                  "2" {:bill-identifier/id "2" :bill-identifier/name "Albert"
                       :bill-identifier/kind :bill-identifier.kind/heading}
                  "3" {:bill-identifier/id "3" :bill-identifier/name "Brian"
                       :bill-identifier/kind :bill-identifier.kind/actual :bill-identifier/heading-id 2}
                  "4" {:bill-identifier/id "4" :bill-identifier/name "Charlie"
                       :bill-identifier/kind :bill-identifier.kind/actual :bill-identifier/heading-id 2}}}}]
    (un-alphabetify st table kind-attribute heading-attribute heading-kind-attribute-value)))

(defn grouped->st [st table target-attribute mk-heading assoc-actual grouped]
  (let [heading-entities (mapv mk-heading (keys grouped))
        entities-into-st (utl/entities-into-st-rf table)
        st0 (entities-into-st heading-entities st)]
    (reduce (fn [{:keys [db] :as st} [letter bill-identifiers]]
              (let [ids (mapv table bill-identifiers)
                    {heading-id :bill-identifier/id} (fe-q/triple->entity db [table target-attribute letter])]
                (utl/nothing false {:letter letter :letter-id heading-id :bill-identifiers ids})
                (reduce (fn [st id]
                          (assoc-actual st table id heading-id))
                  st
                  ids)))
      st0
      grouped)))

(defn letters-grouped-by [target-attribute entities each-size]
  (->> entities
    (map (juxt target-attribute identity))
    (sort-by first)
    (partition-all each-size)
    (map (fn [group]
           (let [all-entities (vec (map second group))
                 begin (first group)
                 end (last group)
                 skey (str (first begin) " -> " (first end))]
             [skey all-entities])))
    (into {})))

(comment
  (let [in [{:a "a"} {:a "as"} {:a "asd"} {:a "aa"} {:a "asdf"} {:a "qwer"} {:a "qwef"}]]
    (letters-grouped-by :a in 3)))

;;
;; Creates the heading entities that can group all the existing table entities. There will then be more of
;; these entities but they will be of two different kinds.
;;
(defn alphabetify [{:keys [db] :as st} table target-attribute mk-heading assoc-actual min-group-size max-visible-squares]
  (let [entities (s/select [table s/MAP-VALS #(= false (:bill-identifier/removed? %))] db)
        ;; Get rid of this soon
        already-done? (boolean (s/select-any [s/ALL :bill-identifier/kind] entities))]
    (utl/assrt (not already-done?) ["alphabetify already done" {:table table :target-attribute target-attribute}])
    (let [grouped1 (most-letters-group-by target-attribute entities (dec min-group-size))
          num (count grouped1)]
      (if (> num max-visible-squares)
        (let [each-count (inc (quot (count entities) max-visible-squares))
              grouped2 (letters-grouped-by target-attribute entities each-count)
              num (count grouped2)]
          (utl/assrt (<= num max-visible-squares) ["Expected 2nd to work" {:num num :each-count each-count}])
          (grouped->st st table target-attribute mk-heading assoc-actual grouped2))
        (grouped->st st table target-attribute mk-heading assoc-actual grouped1)))))

;;
;; Menu/products may come from a BE database (when user part of an organisation). Done on startup. If not enough internet to
;; get them then no point. This app is designed to work with poor internet - see eventual ns.
;;
(defn inject-menu [st items]
  (utl/nothing false "inject-menu" {:items items})
  (let [table :item/id
        ns-keys (utl/ns-keys-rf table)
        transform-keys (utl/transform-map-keys-rf r-domain/item-rama->fe)
        items-into-st (utl/entities-into-st-rf table)]
    (->> st
      ;; Do this (dissoc) before merging as example menu gives new random ids and we don't want duplicates
      (s/setval [:db :item/id] s/NONE)
      ;; These 2 for failures as go, which will cause a "Save (n)" button to appear.
      ;; Thus can keep working w/out the internet. Actually we did auto-saving...
      (s/setval [:bes-changes-queue] [])
      (s/setval [:bes-removes-queue] [])
      (items-into-st (mapv (comp transform-keys ns-keys) items)))))

(defn inject [st path value]
  (utl/assrt (vector? path) ["Always need to inject a path (path is a vector)" {:path path}])
  (->> st
    (s/setval path value)))

(defn take-out [st path]
  (utl/assrt (vector? path) ["Always need to take out a path (path is a vector)" {:path path}])
  (utl/nothing false "Taking out (from reactive loop)" {:path path})
  (s/setval path s/NONE st))

;;
;; When doing many they must be top level keys
;;

(defn inject-many [st keywords values]
  (utl/assrt (every? vector? [keywords values]) ["vectors required" {:keywords keywords :values values}])
  (reduce (fn [st [kw val]]
            (s/setval [kw] val st))
    st
    (map vector keywords values)))

(defn take-out-many [st keywords]
  (utl/assrt (every? keyword? keywords) ["vector of keywords required" {:keywords keywords}])
  (reduce (fn [st kw]
            (s/setval [kw] s/NONE st))
    st
    keywords))

(comment
  (let [kws [:a :b :c]
        vals [1 2 3]]
    (map vector kws vals)))

(defn inject-true [st path]
  (inject st path true))

(defn inject-false [st path]
  (inject st path false))

(defn clear [st path]
  (utl/assrt (vector? path) ["Always need to clear a path (path is a vector)" {:path path}])
  (s/setval path s/NONE st))

(comment
  (let [entities [{:bill-identifier/id "1",
                   :bill-identifier/name "Adam",
                   :ts/updated 1,
                   :bill-identifier/kind :bill-identifier.kind/actual,
                   :bill-identifier/heading-id "SG26CXRFW9HJ7OPU"}]]
    (s/select-any [s/ALL :bill-identifier/kind] entities)))

(comment
  (let [in ["a" "as" "asd" "aa" "asdf" "qwer" "qwef"]]
    (most-letters-group-by identity in 2)))
