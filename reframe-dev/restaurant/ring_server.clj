(ns restaurant.ring-server
  (:require
   [clojure.edn :as edn]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.not-modified :refer [wrap-not-modified]]
   [ring.middleware.resource :refer [wrap-resource]]
   [ring.util.response :as response]
   [com.pangglow.util :as utl]))

(defn save-product [product]
  (println "Pretending to save" product))

(defn read-edn-body [req]
  (when-let [body (:body req)]
    (edn/read-string (slurp body))))

;; Necessary b/c I've chosen to use URL path instead of query params.
;; When there is no param (some id) we return the 'command' in second position
(defn split-last-slash [uri]
  (if (string? uri)
    (let [idx (.lastIndexOf uri "/")]
      (if (pos? idx)
        (if (= (dec (count uri)) idx)
          ["" ""]
          [(subs uri 0 idx) (subs uri (inc idx))])
        [uri ""]))
    [uri ""]))

(comment
  ;; No trailing slash is essential
  (= ["/api" "config"] (split-last-slash "/api/config"))
  (= ["" ""] (split-last-slash "/api/config/"))
  (second (split-last-slash "/api/products/164J0XYOR0OP335J"))
  (split-last-slash nil)
  (split-last-slash 0)
  (split-last-slash "")
  (split-last-slash "whatever")
  )

(defn edn-response [x]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str x)})

(defn api-routes [{:keys [uri request-method] :as req}]
  #_(println "api-routes" req)
  (case request-method
    :get
    (let [[uri param] (split-last-slash uri)]
      (utl/nothing false "api-routes" {:uri uri :param param})
      (if (empty? param)
        {:status 400
         :body (str "Missing param when GET " uri)}
        (case uri

          "/api"
          (case param
            "config"
            (let [m (utl/read-resource "public/restaurant/with-customer.edn")]
              #_(pprint/pprint m)
              (edn-response m))

            ;; Not an API route
            nil)

          ;; Not an API route
          nil)))
    :post
    (case uri
      "/api/products/save"
      ;; If middleware already parsed it
      #_(let [product (:edn-params req)] ; Just grab the parsed data
          {:status 200
           :body (pr-str (save-product product))})
      (let [product (read-edn-body req)]
        {:status 200
         :body (pr-str (save-product product))})

      ;; Not an API route
      nil)))

(defn app-handler [req]
  (case (:uri req)
    "/demo" (response/redirect "/demo.html")
    (let [res (api-routes req)]
      (if-not res
        (response/not-found "Not found")
        res))))

(defn app [req]
  ((-> app-handler
       (wrap-resource "public")
       wrap-content-type
       wrap-not-modified) req))

#_(defn -main [& args]
  ;; Run on different port than Electric, which is 8081 as long as nothing already at 8081
  (println "Starting Jetty in PROD")
  (run-jetty app {:port 8082 :join? false}))

;; Store the server instance
(defonce server (atom nil))

(defn stop-server []
  (when-let [s @server]
    (.stop s)
    (reset! server nil)))

(defn start-server []
  (stop-server)
  (reset! server
    (run-jetty app
      {:port 8082 :join? false})))

(comment
  @server
  (stop-server)
  (start-server))
