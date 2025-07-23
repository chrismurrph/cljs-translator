(ns com.pangglow.js-interop
  (:require
   [clojure.string :as str]
   #?(:cljs [com.pangglow.random :as random])
   [com.pangglow.util :as utl]
   [restaurant.domain :as r-domain]))

(defn pathname []
  #?(:cljs (.-pathname js/window.location)
     :clj nil))

(defn tab-id []
  #?(:cljs
     (or (.getItem js/sessionStorage "tab-instance-id")
       (let [new-id (random/random-id)]
         (.setItem js/sessionStorage "tab-instance-id" new-id)
         new-id))
     :clj "Wrong to ask for tab-id from server"))

(defn clear-value [dom-id]
  #?(:cljs
     (when dom-id
       (set! (.-value (.getElementById js/document dom-id)) ""))
     :clj (comment dom-id)))

;; For checkboxes and radio buttons, you might want to use the checked property instead of value.
(defn get-value [dom-id]
  #?(:cljs
     (let [element (when dom-id (.getElementById js/document dom-id))]
       (when element
         (.-value element)))
     :clj (comment dom-id)))

(defn now []
  #?(:cljs (js/Date.now)
     :clj (.getTime (utl/now))))

(defn top-left [dom-node]
  (let [dom-rect (.getBoundingClientRect dom-node)]
    [(.-x dom-rect) (.-y dom-rect) (.-width dom-rect) (.-height dom-rect)]))

(defn dom-id->element [dom-id]
  #?(:cljs
     (when dom-id (.getElementById js/document dom-id))
     :clj (comment dom-id)))

(defn dom-id->dims [dom-id]
  (some-> dom-id dom-id->element top-left))

;;
;; If always use a dynamic topleft then is fine in face of scrollbar changes, so can't make a
;; -rf of this.
;;
(defn e->point
  ([[topleft-x topleft-y :as top-left] e]
   (let [x (.-clientX e)
         y (.-clientY e)]
     (utl/assrt (every? number? top-left) ["" {}])
     [(- x topleft-x) (- y topleft-y)]))
  ([e]
    (e->point [0 0] e)))

(defn point->element [[x y :as point]]
  (utl/assrt (every? number? point) ["" {}])
  #?(:cljs
     (.elementFromPoint js/document x y)
     :clj (comment [x y])))

(defn dom-id->parent [dom-id]
  (.-parentNode (dom-id->element dom-id)))

;;
;; If you call HTMLElement.focus() from a pointerdown event handler,
;; you must call event.preventDefault() to keep the focus from leaving the HTMLElement
;;
(defn focus-on-element [element]
  #?(:cljs
     (js/setTimeout
       #(.focus element))
     :clj (comment element)))

;;
;; Works when called from an event.
;; Maybe we need to put it in a timer (setTimeout) to have it working always.
;; How to call on a setTimeout yet have a return value, so need a callback?
;; (Just research haven't yet done)
;;
(defn focused-dom-id [reason]
  (utl/nothing false reason)
  #?(:cljs
     (let [activeElement (.-activeElement js/document)
           id (.-id activeElement)]
       [activeElement id])
     :clj nil))

;;
;; reason is only thing have to put into opts. dbg? if you want it true.
;; times is used here recursively, so not for caller to put in
;;
(defn focus-on-dom-id-1 [dom-id opts]
  (utl/assrt (map? opts) ["focus-on-id using opts now" {:opts opts}])
  (let [{:keys [reason times dbg?] :or {dbg? false}} opts
        times (or times 0)
        element (dom-id->element dom-id)]
    (utl/assrt (string? reason) "Must have a reason")
    (if element
      (do
        (if (-> times zero? not)
          (utl/nothing dbg? "After a wait, have found element to focus on" {:element element :times times :id dom-id})
          (utl/nothing dbg? "Immediately found element to focus on" {:element element :id dom-id}))
        (focus-on-element element)
        (let [[active-element focused-dom-id] (focused-dom-id reason)]
          (when-not (= dom-id focused-dom-id)
            ;; Reports that it doesn't work, even when it does.
            (utl/nothing false "WARN: focus-on-id did not work" {:dom-id dom-id :focused-dom-id focused-dom-id :active-element active-element :reason reason}))))
      (do
        (utl/nothing dbg? "Not found element to focus on"
          {:times times
           :reason reason
           :dom-id dom-id
           :possible-explanation "Not currently on the dom"})
        (when (zero? times)
          #?(:cljs
             (js/setTimeout
               #(focus-on-dom-id-1 dom-id (assoc opts :times 1)))
             :clj nil))))))

(def focus-on-dom-id focus-on-dom-id-1)

(defn node-list->list [node-list]
  #?(:cljs
     (let [elements (array-seq node-list)]
       (map (fn [el]
              {:element el
               :id (.-id el)
               :tag-name (.toLowerCase (.-tagName el))})
         elements))
     :clj (comment node-list)))

;; Above fn (focus-on-dom-id-1) makes no sense re. times
(defn focus-on-dom-id-2 [dom-id]
  #?(:cljs
     (js/setTimeout
       #(let [element (dom-id->element dom-id)]
          (if element
            (focus-on-element element)
            (let [input-elements (node-list->list (.querySelectorAll js/document "input"))]
              (utl/always "Not found element to focus on" {:dom-ids (node-list->list (.querySelectorAll js/document "[id]"))
                                                           :inputs input-elements})
              (when (= 1 (count input-elements))
                (focus-on-element (-> input-elements first :element))))))
       500)
     :clj (comment dom-id)))

(defn focus-on-dom-id-3 [dom-id]
  #?(:cljs
     (js/setTimeout
       #(let [element (dom-id->element dom-id)]
          (if element
            (do
              (println "Found element so will focus on it" {:dom-id dom-id})
              (focus-on-element element))
            (println "Could not find element to focus on" {:dom-id dom-id})))
       1000)
     :clj (comment dom-id)))

(defn focus-on-parent [dom-id reason]
  (let [parent (dom-id->parent dom-id)]
    (println "Wanting to focus on parent (non-focusing move!)" {:id dom-id :reason reason :parent parent})
    (focus-on-element parent)))

;;
;; I thought this might give same as activeElement but doesn't.
;;
(defn node-dom-id [dom-node]
  (let [res (.-id dom-node)]
    #_(utl/assrt ((some-fn nil? seq) res) ["node-id, not expecting blank" {:dom-node dom-node}])
    (when (not= "" res)
      res)))

;;
;; Yet to modify this for various Apple devices
;;
(defn agent-touch? [agent-str]
  (if (str/includes? agent-str "Android")
    true
    (if (str/includes? agent-str "x86_64")
      false
      nil)))

(comment
  (agent-touch? "bbx86_64bAndoidbbss"))

(defn go-back! []
  #?( :cljs (.back (.-history js/window))
      :clj nil))

(defn add-event-listener [s f]
  (utl/nothing false "add-event-listener" {:s s})
  #?(:cljs (.addEventListener js/window s f)
     :clj (comment [s f])))

(defn set-title [title]
  #?(:cljs (set! (.-title js/document) title)
     :clj (comment title)))

(defn set-route [route]
  #?(:cljs
     (do
       (utl/assrt (str/starts-with? route "/") ["route must start with a slash" {:route route}])
       (set! (.-location js/window) route))
     :clj (comment route)))

;;
;; touch? is whether touch screen or not.
;; media-touch? always returns false
;; So we will resort to using the agent string: agent-touch?
;;
(defn platform-info []
  #?(:cljs (let [user-agent (.-userAgent js/window.navigator)]
             {:media-touch? (.-matches (.matchMedia js/window "(pointer;coarse)"))
              :agent-touch? (agent-touch? user-agent)
              :user-agent user-agent
              :os (.-platform js/window.navigator)
              :viewport {:width (.-innerWidth js/window) :height (.-innerHeight js/window)}
              :screen {:width (.-width js/screen) :height (.-height js/screen)}})
     :clj nil))

(defn seconds->human-readable [seconds]
  #?(:cljs (let [date (js/Date. (* seconds 1000))]
             (.toISOString date))
     :clj (comment seconds)))

(defn secure? []
  #?(:cljs (str/starts-with? (.-origin js/window.location) "https")
     :clj nil))

(defn hostname []
  #?(:cljs (.-hostname js/window.location)
     :clj "my-hostname"))

(defn cookie-attrs [secure?]
  (str/join ";"
    (remove nil? ["path=/"
                  (str "max-age=" r-domain/max-age)
                  (when secure? "secure")
                  (when secure? "samesite=strict")
                  (when secure? (str "domain=" (hostname)))])))

(comment
  (cookie-attrs true))

(defn create-cookie [cookie-name]
  #?(:cljs (let [new-id (random/random-id)
                 secure? (secure?)]
             (set! js/document.cookie
               (str cookie-name "=" new-id
                 ";" (cookie-attrs secure?)))
             new-id)
     :clj (comment cookie-name)))

(defn debug-cookies []
  #?(:cljs (let [loc js/window.location]
             {:cookies js/document.cookie
              :origin (.-origin loc)
              :hostname (.-hostname loc)
              :protocol (.-protocol loc)
              :secure? (str/starts-with? (.-origin loc) "https")})
     :clj nil))

(def example-cookies "cjConsent=MHxOfDB8Tnww; cjUser=4290bf79-1718-4cc8-9763-0e6ad9947cd1; __pdst=48b4a87c26bd488fb1f3ba8b8fe9096f; _li_dcdm_c=.pangglow.com; _lc2_fpi=34f14801be37--01jj0d129hrazjpx9w9hmb5sef; _lc2_fpi_js=34f14801be37--01jj0d129hrazjpx9w9hmb5sef; _gcl_au=1.1.801639482.1737327939; _ga=GA1.1.1598586783.1737327939; _ga_QTJQME5M5D=GS1.1.1737330169.2.0.1737330169.60.0.0; sessionId=MISB6WSWSS8P8OWS; sessionId=K5A01U66COPYQQHB")

(comment
  (let [cookie-name "sessionId"
        cookies (mapv str/trim (str/split example-cookies #";"))]
    (-> (filter #(str/starts-with? % (str cookie-name "=")) cookies)
      first
      (str/split #"=")
      second)))

(defn find-cookie [cookie-name]
  #?(:cljs (let [cookies (mapv str/trim (-> js/document
                                          .-cookie
                                          (str/split #";")))]
             (-> (filter #(str/starts-with? % (str cookie-name "=")) cookies)
               first
               (str/split #"=")
               second))
     :clj (comment cookie-name)))