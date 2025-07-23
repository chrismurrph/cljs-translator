(ns com.pangglow.query.front-end
  (:require
   [com.pangglow.util :as utl]
   [com.rpl.specter :as s]))

;; We have another one of these somewhere, lets reconcile...
(defn valid-id? [id]
  ((every-pred string? (comp (partial = 16) count)) id))

(defn table-entities [db table]
  (utl/assrt (-> db :db not) ["table-entities, Need to go down into the :db of the re-frame :db" {:better-db (:db db)}])
  (utl/assrt (utl/table? table) ["Expected a table in table-entities" {:not-table table}])
  (utl/assrt (map? db) ["table-entities, Expected a map" {:db db :table table}])
  (s/select [table s/MAP-VALS] db))

(defn some-table-entities [db table]
  (utl/assrt (-> db :db not) ["some-table-entities, Need to go down into the :db of the re-frame :db" {:better-db (:db db)}])
  (utl/assrt (utl/table? table) ["Expected a table in some-table-entities" {:not-table table}])
  (utl/assrt (map? db) ["some-table-entities, Expected a map" {:db db :table table}])
  (let [exists? ((every-pred map? seq) (s/select-one [table] db))]
    exists?))

;; db arg has to be Fulcro shaped
;; Intended to be used with a nil id
;; (In which case nil will be returned)
(defn ident->entity
  ([db [table id :as ident] call-dbg]
   (utl/assrt (-> db :db not) ["ident->entity, Need to go down into the :db of the re-frame :db" {:call-dbg call-dbg :inner-db (:db db)}])
   (utl/assrt (utl/table? table) ["Expected a table in ident->entity" {:ident ident :call-dbg call-dbg}])
   (when id
     (utl/assrt (utl/ident? ident) ["Not proper ident" {:ident ident :call-dbg call-dbg}])
     (s/select-one [table id] db)))
  ([db ident]
   (ident->entity db ident "none given")))

;;
;; Not used but s/be every time need to get a whole load of entities into app st.
;; Or is there another like it used instead?
;; TODO
;; Likely there is - find it and use one.
;;
(defn entities-into-db [table entities]
  (let [key->entity (->> entities
                      (map (juxt table identity))
                      (into {}))]
    {table key->entity}))

;;
;; If have the id don't use this - instead use ident->entity and destructure.
;; Here is for when you only know the value of an attribute (not the id attribute obviously!)
;; i.e. we haven't got the id of the table in question, we have one of the other attributes
;; attribute-value can be a scalar or an ident.
;;
(defn triple->entity [db [table attribute-key attribute-value :as triple]]
  (utl/assrt (-> db :db not) ["triple->entity, Need to go down into the :db of the re-frame :db" {:better-db (:db db)}])
  (utl/assrt (= 3 (count triple)) ["Expected a triple" {:triple triple}])
  (utl/assrt (utl/table? table) ["Expected a table in target->entity" {:not-table table}])
  (utl/assrt (qualified-keyword? attribute-key) ["" {}])
  (s/select-one [table s/MAP-VALS #(= (attribute-key %) attribute-value)] db))

(comment
  (let [bills [{:bill/id "8RQP6LE7ZARID9YQ", :ts/created "1724491226760", :bill/identifier "Orange"}
               {:bill/id "SMBE8T6UV4148W9N", :ts/created "1724495933144", :bill/identifier "Yellow"}
               {:bill/id "AMHGB3F7W7EVMGZ9", :ts/created "1724495941439", :bill/identifier "Green"}]
        db (entities-into-db :bill/id bills)]
    #_(triple->entity db [:bill/id :bill/identifier "Yellow"])
    #_(ident->entity-2 db [:bill/id "SMBE8T6UV4148W9N"])
    (some-table-entities db :bill/id)))

(defn- id->entity-rf [db table]
  (fn [id]
    (ident->entity db [table id])))

(defn ids->entities [db table ids]
  (utl/assrt (qualified-keyword? table) ["" {}])
  (let [id->entity (id->entity-rf db table)]
    (mapv id->entity ids)))

(defn idents->entities [db idents call-dbg]
  (vec (keep (fn [ident]
               (ident->entity db ident call-dbg))
         idents)))

(defn probe-idents->entities [db idents call-dbg]
  (let [res (idents->entities db idents call-dbg)]
    (utl/always "idents->entities" {:idents idents :call-dbg call-dbg :entities res})
    res))

(defn entities->idents-rf [table]
  (utl/assrt (qualified-keyword? table) ["" {}])
  (fn [entities]
    (utl/assrt (every? (comp valid-id? table) entities) ["Not all entities are valid" {:entities entities}])
    (mapv (fn [entity]
            (let [id-value (get entity table)]
              (utl/assrt id-value ["" {}])
              [table id-value]))
      entities)))

(defn entities->idents [table entities]
  (let [entities->idents (entities->idents-rf table)]
    (entities->idents entities)))

(defn entity->ident [table entity]
  [table (get entity table)])

