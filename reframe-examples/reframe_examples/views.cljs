(ns reframe-examples.views)

(defn main-view []
  [:<>
   [:h1 "Hello from Electric Clojure"]
   [:p "Source code for this page is in "
    [:code "src/electric_start_app/main.cljc"]]
   [:p "Make sure you check the "
    [:a {:href "https://electric.hyperfiddle.net/" :target "_blank"}
     "Electric Tutorial"]]])