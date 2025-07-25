(ns electric-starter-app.ring-middleware
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [contrib.assert :refer [check]]
   [contrib.template :refer [template]]
   [ring.middleware.basic-authentication :as auth]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.cookies :as cookies]
   [ring.middleware.resource :refer [wrap-resource]]
   [ring.middleware.not-modified :refer [wrap-not-modified]]
   [ring.util.response :as res]
   [ring.websocket :as ws]))

(defn authenticate [username _password] username) ; demo (accept-all) authentication

(defn wrap-demo-authentication "A Basic Auth example. Accepts any username/password and store the username in a cookie."
  [next-handler]
  (-> (fn [ring-req]
        (let [res (next-handler ring-req)]
          (if-let [username (:basic-authentication ring-req)]
            (res/set-cookie res "username" username {:http-only true})
            res)))
    (cookies/wrap-cookies)
    (auth/wrap-basic-authentication authenticate)))

(defn wrap-authenticated-request [next-handler]
  (fn [ring-request]
    (next-handler (auth/basic-authentication-request ring-request authenticate))))

(defn wrap-demo-router "A basic path-based routing middleware"
  [next-handler]
  (fn [ring-req]
    (case (:uri ring-req)
      "/auth" (let [response  ((wrap-demo-authentication next-handler) ring-req)]
                (if (= 401 (:status response)) ; authenticated?
                  response                     ; send response to trigger auth prompt
                  (-> (res/status response 302) ; redirect
                    (res/header "Location" (get-in ring-req [:headers "referer"]))))) ; redirect to where the auth originated
      ;; For any other route, delegate to next middleware
      (next-handler ring-req))))

(defn get-modules [manifest-path]
  (when-let [manifest (io/resource manifest-path)]
    (let [manifest-folder (when-let [folder-name (second (rseq (str/split manifest-path #"\/")))]
                            (str "/" folder-name "/"))]
      (->> (slurp manifest)
        (edn/read-string)
        (reduce (fn [r module] (assoc r (keyword "hyperfiddle.client.module" (name (:name module)))
                                 (str manifest-folder (:output-name module)))) {})))))

(defn wrap-index-page
  "Server the `index.html` file with injected javascript modules from `manifest.edn`.
`manifest.edn` is generated by the client build and contains javascript modules
information."
  [next-handler config]
  (fn [ring-req]
    (if-let [response (res/resource-response (str (check string? (:resources-path config)) "/index.html"))]
      (if-let [bag (merge config (get-modules (check string? (:manifest-path config))))]
        (-> (res/response (template (slurp (:body response)) bag)) ; TODO cache in prod mode
          (res/content-type "text/html")
          (res/header "Last-Modified" (get-in response [:headers "Last-Modified"])))
        (-> (res/not-found (pr-str ::missing-shadow-build-manifest)) ; can't inject js modules
          (res/content-type "text/plain")))
      ;; index.html file not found on classpath
      (next-handler ring-req))))

(defn not-found-handler [_ring-request]
  (-> (res/not-found "Not found")
    (res/content-type "text/plain")))

(defn wrap-always-revalidate [next-handler]
  ;; https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control#up-to-date_contents_always
  (fn [ring-req]
    (assoc-in (next-handler ring-req) [:headers "Cache-Control"]
      ;; no-cache doesn't mean don't cache but rather "always revalidate".
      ;; must-revalidate ensures all intermediate caches (not just proxies) will revalidate.
      "no-cache, must-revalidate")))

(defn http-middleware [config]
  ;; these compose as functions, so are applied bottom up
  (-> not-found-handler
    (wrap-index-page config) ; 4. otherwise fallback to default page file
    (wrap-resource (:resources-path config)) ; 3. serve static file from classpath
    (wrap-always-revalidate)
    (wrap-not-modified) ; 3. ensure cached resources are re-validated
    (wrap-content-type) ; 2. detect content (e.g. for index.html)
    (wrap-demo-router) ; 1. route
    ))

(defn wrap-gate
  "Allows ring requests to pass through to the `next-ring-handler` when `(open-fn? ring-request)` is true.
  Otherwise, return the value of `(on-closed-fn ring-request)`."
  [next-ring-handler {::keys [open-fn? on-closed-fn]
                      :or    {open-fn?     (constantly true)
                              on-closed-fn (constantly {:status 423, :body "Locked"})}}] ; HTTP 423 Locked, browsers interprets it as a generic 400
  (fn [ring-request]
    (if (open-fn? ring-request)
      (next-ring-handler ring-request)
      (on-closed-fn ring-request))))

(defn wrap-allow-ws-connect
  "Allow websocket upgrade requests to pass through if `(accept-ws-connect-fn ring-request)` is true.
  Use case: prevent electric client to connect to a dev server until the server is ready."
  [next-handler accept-ws-connect-fn]
  (wrap-gate next-handler
    {::open-fn?    (if-not accept-ws-connect-fn
                     (constantly true)
                     (fn [ring-request]
                       (if (ws/upgrade-request? ring-request)
                         (accept-ws-connect-fn ring-request)
                         true)))
     ::on-closed-fn (fn [_ring-request] {:status 423 ; HTTP 423 Locked, not using 503 because it's not an error. Browsers interprets 423 as a generic 403.
                                         :body "Server is not ready to accept WS connections. Try again later."})}))

