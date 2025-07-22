(ns restaurant.css
  (:require [clojure.java.io :as io]
            [com.rpl.specter :as s]
            [shadow.cljs.devtools.server.fs-watch :as fs-watch]
            [shadow.css.build :as cb]))

(defonce css-ref (atom nil))
(defonce css-watch-ref (atom nil))

(def input-dir-1 (io/file "src"))
;; When get back to using hopefully it will 'just work'.
(def input-dir-2 (io/file "src-contrib"))
(def output-dir (io/file "resources" "public" "css"))

(defn generate-css []
  (let [result
        (-> @css-ref
            (cb/generate '{:ui
                           {:entries [restaurant.styles]}})
            (cb/write-outputs-to output-dir))]

    (println :CSS-GENERATED)
    (doseq [mod (:outputs result)
            {:keys [warning-type] :as warning} (:warnings mod)]
      (println [:CSS (name warning-type) (dissoc warning :warning-type)]))
    (println)))

(comment
  (generate-css))

(defn index-files []
  (-> (cb/start)
    (cb/index-path input-dir-1 {})
    (cb/index-path input-dir-2 {})))

(defn namespace->css-rf [index-files]
  (fn [ns]
    (s/select [:namespaces (s/keypath ns) :css s/ALL :form] index-files)))

(comment
  "Collects the CSS per namespace"
  (let [index-files (index-files)
        namespace->css (namespace->css-rf index-files)
        namespaces (s/select [:namespaces s/MAP-KEYS] index-files)]
    (->> (map (juxt identity namespace->css) namespaces)
      (filterv (comp seq second)))))

(comment
  (->> (index-files)
    (s/select [:namespaces s/MAP-VALS :css seq s/ALL :form])))

(defn start
  {:shadow/requires-server true}
  []

  ;; first initialize my css
  (reset! css-ref (index-files))

  ;; then build it once
  (generate-css)

  ;; then setup the watcher that rebuilds everything on change
  (reset! css-watch-ref
    (fs-watch/start
      {}
      [input-dir-1]
      ["cljs" "cljc" "clj"]
      (fn [updates]
        (try
          (doseq [{:keys [file event]} updates
                  :when (not= event :del)]
                 ;; re-index all added or modified files
            (swap! css-ref cb/index-file file))

          (generate-css)
          (catch Exception e
            (prn :css-build-failure)
            (prn e))))))

  ::started)

(defn stop []
  (when-some [css-watch @css-watch-ref]
    (fs-watch/stop css-watch)
    (reset! css-ref nil))

  ::stopped)

;; Called from dev
(defn go []
  (stop)
  (start))

;; (stop) and (start) here to see clearly when CSS is acting strangely.
;; As an example sometimes a utility from tailwind docs does not exist and the thing doesn't build - has an error
;; that can be seen here.
;; To workaround just use the underlying CSS from the docs (https://tailwindcss.com/docs/).
(comment
  (stop)
  (start)
  @css-ref)
