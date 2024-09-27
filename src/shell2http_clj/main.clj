(ns shell2http-clj.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [babashka.process :as process]
            [babashka.cli :as cli]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.core.protocols :refer [StreamableResponseBody]]
            [promesa.exec.csp :as csp]
            [hiccup2.core :as h])
  (:import (promesa.exec.csp.channel Channel)))

(set! *warn-on-reflection* true)

(defonce options (atom nil))

(defn args-ok? [args]
  (and (< 1 (count args))
       (even? (count args))
       (every? #(= \/ (ffirst %)) (partition 2 args))))

(defn parse-urls [urls]
  (apply hash-map urls))

(defn show-help
  [spec]
  (cli/format-opts (merge spec {:order (vec (keys (:spec spec)))})))

(def cli-spec
  {:spec
   {:help     {:coerce :boolean
               :desc   "prints this info and exits"}
    :no-index {:coerce  :boolean
               :desc    "don't generate index page"
               :default false}
    :add-exit {:coerce  :boolean
               :desc    "add /exit command"
               :default false}
    :echo     {:coerce  :boolean
               :default false
               :desc    "reprints output to stdout"}
    :port     {:coerce  :number
               :default 8080
               :desc    "port for http server (default 8080)"}}
   :coerce     {:urls []}
   :validate   {:urls args-ok?}
   :args->opts [:urls]})


(extend-type Channel
  StreamableResponseBody
  (write-body-to-stream [channel response output-stream]
    (csp/go (with-open [writer (io/writer output-stream)]
             (loop []
               (when-let [msg ^String (csp/<! channel)]
                 (doto writer (.write msg) (.flush))
                 (recur)))))))

(defn index-handler [_]
  (let [urls (:urls @options)]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body
     (str
      (h/html
          (into [:table]
                (for [[url command] urls]
                  [:tr [:td [:a {:href url} url]] [:td command]]))))}))


(defn main-handler [request]
  (let [uri  (:uri request)]
    (case uri
      "/"
      (index-handler request)

      {:status  404
       :headers {"Content-Type" "text/html"}
       :body    "Not found"})))

(defn async-handler [request respond raise]
  (let [uri     (:uri request)
        command (get (:urls @options) uri)]
    (if command
      (let [ch (csp/chan)]
        (respond {:status 200 :headers {} :body ch})
        (csp/go (doseq [line (line-seq (io/reader (:out (process/process command {:err :out}))))]
                 (when (:echo @options) (println line))
                 (csp/>! ch (str line "\n")))
               (csp/close! ch)))

      (respond (main-handler request)))))

(defonce jetty-server (atom {:instance nil
                             :config   nil}))

(defn stop-jetty-server []
  (println "Stopping server...")
  (.stop ^org.eclipse.jetty.server.Server (:instance @jetty-server)))

(defn start-jetty-server [& [config]]
  (let [config (merge {:port 3000
                       :join? false
                       :async? true} config)]
    (println "Starting server on port" (:port config) "...")
    (swap! jetty-server assoc :instance (run-jetty #'async-handler config)
           :config config)
    (println "Server started")))

(.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable stop-jetty-server))

(defn -main
  [& args]
  (let [{:keys [opts args]} (cli/parse-args args cli-spec)]
    (prn :opts opts :args args)
    (reset! options opts)
    (swap! options update :urls parse-urls)
    (if (or (:help opts) (:h opts))
      (println (show-help cli-spec))
      (start-jetty-server {:port (:port opts)}))))

(comment
  (start-jetty-server {:port 3000})
  (stop-jetty-server)


  (parse-urls ["/" "python bla.py"])

  (into {} ["/" "python bla.py"])
  (apply hash-map  ["/" "python bla.py" "/test" "ls"] )
  (cli/parse-args ["/" "python bla.py" "/asd" "test2"] cli-spec)
  (cli/parse-args ["--help" "/" "x"] cli-spec)
  (cli/parse-args ["--help"] cli-spec)

  )
