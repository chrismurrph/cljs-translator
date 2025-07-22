(ns com.pangglow.random
  (:require
   [nano-id.core :as nano-id]))

(defn assrt [pred msg]
  #?(:cljs (when (not pred)
             (println (str "ASSRT Error Msg: <" msg ">"))
             #_(.error js/console msg)
             (js/error msg))
     :clj (assert pred msg)))

(def choices "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ")

(defonce random-id (nano-id/custom choices 16))

(comment
  (random-id))

(def possible-values (into #{} choices))

(defn random-id? [s]
  (when (string? s)
    (let [right-length? (= 16 (count s))]
      (when right-length?
        (every? possible-values s)))))

;;
;; They stand out. Helps with reading code.
;;
(defn one-char [ch]
  (assrt (char? ch) ["Need a char. Use a backslash, e.g. \\B" {:ch ch :type (type ch)}])
  (let [res (apply str (repeat 16 ch))]
    (if (random-id? res)
      res
      (println "Need to use a capital letter" {:ch ch}))))

(comment
  (one-char \Z))

(defn word [word]
  (assrt (every? (set choices) word) ["Use available digits" {:word word :available choices}])
  (let [going-in (vec (take 16 word))
        diff (- 16 (count going-in))
        extra (repeat diff \0)
        res (apply str (into going-in extra))]
    (if (random-id? res)
      res
      (println "Something wrong with your word" {:word word :res res}))))

;; When longer than 15 word is just the cut off string
(comment
  (let [s "WHATISGOINGONWHENTHERESLOTS"]
    (= (->> s
         (take 16)
         (apply str))
      (word s)))
  )
