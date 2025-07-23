(ns restaurant.ui
  (:require
   [clojure.string :as str]
   [com.pangglow.js-interop :as js-i]
   [com.pangglow.util :as utl]
   [restaurant.domain :as r-domain]
   [restaurant.mutations :as r-muts]))

;;
;; Here 50 is the distance from the right of the screen
;;
(def last-width 50)
(def short-last-width 5)

(defn line [k v]
  (if v
    (str "\n\t" k " " v)
    ""))

(defn started-message [title session-id config test-query platform ms mutator-f]
  (js-i/set-title (str "Pangglow - " title))
  (println (str "Started " title)
    (line :session-id session-id)
    (line :version (r-domain/version))
    (line :git (:git config))
    (line :timestamp (js-i/seconds->human-readable ms))
    (line :platform (dissoc platform :viewport :screen :user-agent))
    (line :test-query test-query)
    "\n\t" {:viewport (:viewport platform)
            :screen (:screen platform)})
  ;; This is having a rude browser dialog pop up in an attempt to stop the user refreshing. Rather than train the
  ;; user to say cancel to it, we should train the user not to refresh in the first place. They will train themselves
  ;; when they lose all their memory data anyway! Not an issue for all apps but certainly for the pos app where food
  ;; server goes away from the Wifi. localstorage will likely be the best solution here.
  ;; What pre-empted this commenting out is going to chat and back again.
  #_(js-i/add-event-listener "beforeunload" (fn [e]
                                              (-> e
                                                (.preventDefault)
                                                (.returnValue ""))))
  (mutator-f r-muts/inject-true [:start-annoucement-made?])
  ;; This is when the "Loading" (TBD) screen can be removed.
  )

(defn pixelate [s]
  (if (map-entry? s)
    (let [[k v] s]
      (utl/nothing false "pixelate the value" {:s s :k k :v v})
      [k (pixelate v)])
    (str s "px")))

(comment
  (let [m {:x 10 :y 5}]
    (pixelate (first m))))

(defn pixelate-map [m]
  (->> m
    (map pixelate)
    (into {})))

(comment
  (let [m {:x 10 :y 5}]
    (pixelate-map m)))

(defn line-columns
  ([xs count]
   (->> xs
     (map pixelate)
     (take count)
     (str/join " ")))
  ([xs]
   (line-columns xs (count xs))))

(comment
  (line-columns [170 240]))

(def phone-bill-line-columns-xs [250 40 40])

