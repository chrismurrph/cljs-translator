(ns restaurant.domain
  (:require
   [com.pangglow.random :as random]
   [com.pangglow.util :as utl]))

(defn version []
  "10.0")

(def cookie-name "sessionId")

(def pultney-id "164J0XYOR0OP335J")
(def alice-id "LS73TDA4RHWT1Y26")
(def bob-id "164J0XYOR0OP335V")
(def james-id "164J0XYOR0OP335T")
(def chris-id "164J0XYOR0OP335U")
(def chris-ident [:user/id chris-id])
(def rach-id "164J0XYOR0OP335V")
(def rach-ident [:user/id rach-id])
(def rica-id "164J0XYOR0OP335W")
(def loreto-id "UUST2710XFHT02ZB")
(def valencia-id "UUST2710XFHT02ZC")

;;
;; The cookie on Incognito Chrome browser on my development machine. Only changes when I reboot my machine.
;;
(def dev-session-id-1 "7A23EXXBG850V7QO" #_"SIANHT2GX0ZLV3WJ" #_"Q101PGQGBWW2RIBB" #_"O17L4HR75MH1W1RG" #_"BWVJD4KKFEZ9M3D4")
;;
;; Normal (non Incognito) browser cookie. Used when want 2 users to chat with one another.
;;
(def dev-session-id-2 "9N04QT63UYZOL7GA")

(def dev-ficticious-session-id (random/word "FICTICIOUS"))

;; 1000 years
(def max-age 31536000000)

(def app-names #{:pos :chat :prods})

(def tables #{:item/id :bill-line/id})

(def item-kinds #{:item.kind/heading :item.kind/product})

(defn by-id [dbg-src e]
  (utl/assrt (string? dbg-src) ["dbg-src not correct" {:dbg-src dbg-src :e e}])
  (utl/assrt (some? e) ["Can't get the id of a nil entity" {:dbg-src dbg-src}])
  (let [old-res (or (:product/id e) (:heading/id e))
        new-res (or (:item/id e) (:bill-line/id e))]
    (utl/assrt (not old-res) ["Found an entity with an old id" {:e e :dbg-src dbg-src}])
    (utl/assrt new-res ["Not an entity with an :item/id or :bill-line/id" {:e e :dbg-src dbg-src}])
    new-res))

(def bill-denominations [[1000 true] [500 true] [200 true] [100 true] [50 true] [20 true]])
(def coin-denominations [[20 false] [10 false] [5 false] [1 false]])
(def denominations (into bill-denominations coin-denominations))

;;
;; On FE always use Datomic keys. So the extra keys here are for converting from Datomic -> Rama when item
;; entities are coming from the FE to the Datomic BE.
;;
(def rama-item-attribs {:created :item/created
                        :ts/created :item/created
                        :price :item/price
                        :product/price :item/price})
(def item-rama->fe {:created :ts/created
                    :item/created :ts/created
                    :updated :ts/updated
                    :item/updated :ts/updated
                    :price :product/price
                    :item/price :product/price
                    ;; For any that are saved by Back End Saver
                    :user-ref :last-updated/user-ref
                    :item/user-ref :last-updated/user-ref
                    :tab-id :last-updated/tab-id
                    :item/tab-id :last-updated/tab-id
                    })
(def organisation-rama->fe {:vendor-kind :vendor/kind
                            :organisation/vendor-kind :vendor/kind})

(defn mk-item
  ([item-attribs desc heading? ms price heading-id id]
   (utl/assrt (string? desc) ["mk-item, desc to be a string" {:desc desc}])
   (utl/assrt (boolean? heading?) ["mk-item, heading? to be a boolean" {:heading? heading?}])
   (utl/assrt (map? item-attribs) ["mk-item, item-attribs to be a map" {:item-attribs item-attribs}])
   (when (not heading?)
     (utl/assrt (number? price) ["mk-item, bad price" {:price price}])
     (utl/assrt (utl/ident-id? heading-id) ["mk-item, bad heading-id 1" {:heading-id heading-id}]))
   (when (and heading? heading-id)
     (utl/assrt (utl/ident-id? heading-id) ["mk-item, bad heading-id 2" {:heading-id heading-id}]))
   (cond-> {:item/id id
            (:created item-attribs) ms
            :item/kind (if heading? :item.kind/heading :item.kind/product)
            :item/description desc}
     (and heading? heading-id) (assoc :item/heading-id heading-id)
     (not heading?) (assoc (:price item-attribs) price :item/heading-id heading-id)))
  ([item-attribs ms desc price heading-id id]
   (mk-item item-attribs desc false ms price heading-id id))
  ([ms item-attribs desc id]
   (mk-item item-attribs desc true ms nil nil id)))

(defn mk-middle-heading [item-attribs ms desc heading-id id]
  (mk-item item-attribs desc true ms nil heading-id id))

;;
;; Bill identifiers don't need a kind. The kind concept is only to differentiate those that are headings.
;; When no kind then they are all actuals.
;;
(defn mk-bill-identifier [ms origin name]
  (utl/assrt (string? name) ["Bad name for a bill identifier" {:name name}])
  (utl/assrt (#{:bill-identifier.origin/original
                 :bill-identifier.origin/user-created
                 ;; Whenever overwrite a user-created we can just remove it. Then don't need this origin
                 #_:bill-identifier.origin/user-overwritten} origin)
    ["When mk-bill-identifier need proper origin" {:name name :origin origin}])
  (let [id (random/random-id)]
    {:bill-identifier/id id
     :ts/created ms
     :bill-identifier/name name
     :bill-identifier/origin origin}))

(def gen-many-text-f (utl/gen-many-text-rf 10))

;;
;; Whenever the chat app opens the user will be talking to support.
;;
(def support-user-id "2PL94JNV5MP0NP9E")

(comment
  (random/random-id))
