(ns dev
  (:require
   #?(:cljs [hyperfiddle.electric-client3])
   #?(:clj [electric-starter-app.server-jetty :as jetty])
   #?(:clj [restaurant.css :as css])
   #?(:clj [shadow.cljs.devtools.api :as shadow])
   #?(:clj [shadow.cljs.devtools.server :as shadow-server])
   #?(:clj [clojure.tools.logging :as log])
   #?(:clj [clj-reload.core :as reload])
   clojure.edn
   [hyperfiddle.electric3 :as e]
   [electric-starter-app.main :as configurable-main]))

(comment (-main)) ; repl entrypoint

#?(:clj ;; Server Entrypoint
   (do
     (def config
       {:host "localhost"
        :port 8080
        :resources-path "public/electric_starter_app"
        :manifest-path ; contains Electric compiled program's version so client and server stays in sync
        "public/electric_starter_app/js/manifest.edn"})

     (defn -main [& args]
       (log/info "Starting Electric compiler and server...")

       (shadow-server/start!)
       (shadow/watch :electric-dev)
       (comment (shadow-server/stop!))

       (def server (jetty/start-server!
                     (fn [ring-request]
                       (e/boot-server {} configurable-main/Main (e/server ring-request)))
                     config))

       (comment (.stop server))
       )))

;; #?(:cljs ;; Client Entrypoint
;;    (do
;;      (def electric-entrypoint (e/boot-client {} configurable-main/Main (e/server (e/amb))))

;;      (defonce reactor nil)

;;      (defn ^:dev/after-load ^:export start! []
;;        (set! reactor (electric-entrypoint
;;                        #(js/console.log "Reactor success:" %)
;;                        #(js/console.error "Reactor failure:" %))))

;;      (defn ^:dev/before-load stop! []
;;        (when reactor (reactor)) ; stop the reactor
;;        (set! reactor nil))))

#?(:cljs ;; Client Entrypoint
   (do
     (defonce reactor nil)

     (defn ^:dev/after-load ^:export start! []
       (set! reactor ((e/boot-client {} configurable-main/Main (e/server (e/amb)))
                      #(js/console.log "Reactor success:" %)
                      #(js/console.error "Reactor failure:" %))))

     (defn ^:dev/before-load stop! []
       (when reactor (reactor)) ; stop the reactor
       (set! reactor nil))))

