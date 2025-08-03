(ns com.pangglow.math
  (:require
   #_(:cljs ["decimal.js-light" :refer [Decimal]])
   [com.pangglow.util :as utl])
  #_(:clj (:import
           (java.math BigDecimal RoundingMode))))

(def PI #?(:clj Math/PI :cljs js/Math.PI))

(defn pow
  [x n]
  #?(:clj (Math/pow x n)
     :cljs (js/Math.pow x n)))

#_(defn round
  [precision n]
  #?(:clj (.doubleValue (.setScale (BigDecimal/valueOf n) (int precision) RoundingMode/HALF_UP))
     :cljs (let [decimal (Decimal. n)]
             (.toNumber (.toDecimalPlaces decimal (int precision) (.-ROUND_HALF_UP Decimal))))))

#_(defn -round-double-dec-pl-rf
  "Round a double to the given number of significant digits"
  [precision]
  (fn [^double d]
    (utl/assrt (number? d) ["Cannot round non-number to precision" {:precision precision :d d}])
    (let [factor (pow 10 precision)]
      (/ (round 0 (* d factor)) factor))))

#_(def round-double-0 (-round-double-dec-pl-rf 0))
#_(def round-double-4 (-round-double-dec-pl-rf 4))

;;
;; npm stuff always causes troubles, so just get rid of it!
;; We should be using ints for everything anyway (to the nearest pixel or the nearest peso)
;; Surely we can do the rounding ourselves
;;
(defn round-double-4 [n]
  (println "WARN, not rounding to 4 dec places, no longer using decimal.js-light" {:n n})
  n)

(defn sin
  [n]
  #?(:clj (Math/sin n)
     :cljs (js/Math.sin n)))

(defn cos
  [n]
  #?(:clj (Math/cos n)
     :cljs (js/Math.cos n)))

(defn atan2
  [point-x point-y]
  #?(:clj (Math/atan2 point-y point-x)
     :cljs (js/Math.atan2 point-y point-x)))

(defn negate [n]
  (* -1 n))

(comment
  (negate 10))

(defn sqrt
  [n]
  #?(:clj (Math/sqrt n)
     :cljs (js/Math.sqrt n)))

(def max-int #?(:cljs js/Number.MAX_SAFE_INTEGER
                :clj Integer/MAX_VALUE))

(comment
  (vector 13 (rand-int Integer/MAX_VALUE))
  (rand-int max-int))

(defn radians-to-degrees
  [radians]
  (* radians (/ 180.0 PI)))

(defn degrees-to-radians
  [degrees]
  (* degrees (/ PI 180.0)))

(defn- cartesian-to-polar
  [[point-x point-y :as point]]
  (utl/assrt (and (number? point-x) (number? point-y)) ["Not a point" point])
  [(sqrt (+ (pow point-x 2) (pow point-y 2))) (atan2 point-x point-y)])

(defn cartesian-to-compass
  "The delta of a line can be given to this to get the slope"
  ([point abs?]
   (let [point (vec point)
         [radius theta] (cartesian-to-polar point)
         slope (round-double-4 (cond-> (radians-to-degrees theta)
                                      abs? abs))]
     #_(println "slope from point" {:slope slope :point point})
     [(round-double-4 radius) slope]))
  ([point]
   (cartesian-to-compass point false)))

(defn polar-to-cartesian
  [[center-x center-y _] radius theta]
  [(+ center-x (round-double-4 (* radius (cos theta))))
   (+ center-y (round-double-4 (* radius (sin theta))))])

(defn compass-to-cartesian
  [center radius n]
  (polar-to-cartesian center radius (degrees-to-radians n)))

(defn fraction->circle-coord [fraction]
  ;; Put .1 on either side for floating point errors
  (utl/assrt (and (>= fraction -0.1) (<= fraction 1.1)) ["Bad fraction" {:fraction fraction}])
  (let [x (cos (* 2 PI fraction))
        y (sin (* 2 PI fraction))]
    [x y]))





