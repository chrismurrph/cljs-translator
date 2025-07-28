(ns restaurant.with-customer.ui
  (:require
   [restaurant.ui :as r-ui]))

(defn generate-absolute-style [top left]
  {:position "absolute"
   :top (r-ui/pixelate top)
   :left (r-ui/pixelate left)})