(ns restaurant.with-customer.domain
  (:require
   [com.pangglow.random :as random]
   [com.pangglow.util :as utl]))

;; These in pixels, static here and in CSS
;; i.e. must update in both places. See :gen/container.
(def container-width 650)
(def container-height 500)

;; Same applies, see :phone/container.
(def phone-width 360)
(def phone-height 600)

(def err-timeout 1500)
;; Gives user time to be pressing "+" or "-" multiple times
(def edit-timeout 1500)

;; It is assumed that this value is odd. It is the number of records the rect contains, same as the actual number of visible records
(def visible-record-count 9)
(def max-convenient-unpaid-bills 5)
(def max-visible-squares 25)
(def max-visible-squares-phone 12)

(comment
  (even? (int (/ visible-record-count 2))))

;;
;; With a dom/on it seeems that if don't do anything (return nil) then propogation goes to a more inner dom/on.
;; So for mouse/touch events we define the area we want the scroll control to handle events for. The east
;; demarcation is for touch so that the "Remove" that pops up on a bill-line will still appear.
;; The south demarcation is for the Home/Back buttons.
;;
(def east-demarcation 90)
(def south-demarcation 400)
(def phone-west-demarcation 210)
(def phone-east-demarcation 260)

(def number-strs ["One" "Two" "Three" "Four" "Five" "Six" "Seven" "Eight" "Nine" "Ten"])
(def customer-bill "Bill")
(def new-bill-str (str "New" " " customer-bill))
(def default-bill-identifiers (mapv #(str customer-bill " " %) number-strs))
(def default-bill-identifiers-set (set default-bill-identifiers))

;; Will need to lookup and add in :bill-line/product of "Bacon Bits" from under "A La Carte"
(defn mk-product-bill-line [ms product-id]
  (let [id (random/random-id)]
    {:bill-line/id id
     :bill-line/product [:item/id product-id]
     :bill-line/quantity 1
     :ts/created ms}))

;; Lookup and add in :bill/ui-heading-id of "A La Carte".
(defn mk-example-bill [ms heading-id desc bill-line-id]
  (let [id (random/random-id)]
    {:bill/id id
     :bill/ui-heading-id heading-id
     :bill/identifier desc
     :ts/created ms
     :bill/bill-lines [[:bill-line/id bill-line-id]]}))

(defn example-bills [heading-id product-id ms]
  (let [pairs (mapv (fn [desc ms]
                      (let [{:bill-line/keys [id] :as bill-line} (mk-product-bill-line ms product-id)
                            bill (mk-example-bill ms heading-id desc id)]
                        [bill bill-line]))
                (->> default-bill-identifiers (drop 1) (take 6))
                (iterate inc ms))
        bills (mapv first pairs)
        bill-lines (mapv second pairs)]
    [bills bill-lines]))

(comment
  (->> (iterate inc 1)
    (take 5)))

(def deep-water-colour "#1E90FF")
(def shallow-water-colour "#ADD8E6")

(defn identifier->idx [identifier]
  (let [m (utl/order-index default-bill-identifiers)
        res (get m identifier)]
    (utl/assrt res ["identifier must be one of these keys" {:keys (keys m) :identifier identifier}])
    res))

(comment
  (utl/order-index default-bill-identifiers))

(def previous-calc-states {:billing-out :view
                           :giving-change :billing-out
                           :checking-out :giving-change})

;;
;; When customer has paid exactly we are in :giving-change but the right map is :bill/received-from-customer-m
;;
(def calc-state->attribute {:giving-change :bill/given-back-m
                            :billing-out :bill/received-from-customer-m})

(comment
  (require '[restaurant.domain :as r-domain])
  (let [st {:db {:bill-identifier/id
                 {"1" {:bill-identifier/id "1" :bill-identifier/name "Adam"}
                  "2" {:bill-identifier/id "2" :bill-identifier/name "Albert"}
                  "3" {:bill-identifier/id "3" :bill-identifier/name "Brian"}
                  "4" {:bill-identifier/id "4" :bill-identifier/name "Charlie"}}}}
        table :bill-identifier/id
        mk-heading (fn [letter]
                     (utl/assrt (string? letter) ["letter not a string" {:letter letter}])
                     {:bill-identifier/id (random/random-id)
                      #_#_:ts/created ms
                      :bill-identifier/kind :bill-identifier.kind/heading
                      :bill-identifier/name letter})
        assoc-actual (fn [st table id heading-id]
                       (s/transform [:db table id] #(assoc %
                                                      #_#_:ts/updated ms
                                                      :bill-identifier/kind :bill-identifier.kind/actual
                                                      :bill-identifier/heading-id heading-id)
                         st))
        alphabetified (r-domain/alphabetify st table :bill-identifier/name mk-heading assoc-actual 2 max-visible-squares)
        kind-attribute :bill-identifier/kind
        heading-attribute :bill-identifier/heading-id
        heading-kind-attribute-value :bill-identifier.kind/heading
        un-alphabetified (r-domain/un-alphabetify alphabetified table kind-attribute heading-attribute heading-kind-attribute-value)]
    (= st un-alphabetified)))