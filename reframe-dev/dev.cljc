(ns dev
  (:require
   #?(:clj [restaurant.ring-server :as server])
   #?(:clj [restaurant.css :as css])
   #?(:clj [shadow.cljs.devtools.api :as shadow])
   #?(:clj [shadow.cljs.devtools.server :as shadow-server])
   #?(:clj [clojure.tools.logging :as log])
   clojure.edn))

;;
;; After have started the REPL. Starts whichever app was configured in configurable-main
;;
(comment
  (-main)) ; repl entrypoint

#?(:clj ;; Server Entrypoint
   (do
     #_(def config
       (merge
         {:host "localhost"
          :port 8081
          :resources-path "public/electric_starter_app"
          :manifest-path ; contains Electric compiled program's version so client and server stays in sync
          "public/electric_starter_app/js/manifest.edn"}
         (try (clojure.edn/read-string (slurp "resources/config.edn")) (catch java.io.FileNotFoundException _))))

     (defn -main [& args]
       (log/info "Starting Ring server and Shadow watch")

       (shadow-server/start!)
       (shadow/watch :reframe-dev)
       (comment (shadow-server/stop!))

       (log/info "About to build and watch CSS")
       (css/go)
       (log/info "Done")

       (server/start-server)
       )))