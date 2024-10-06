(ns shell2http-clj.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [babashka.process :as process]
            [babashka.cli :as cli]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.core.protocols :refer [StreamableResponseBody]]
            [clojure.core.async :as async]
            [hiccup2.core :as h])
  (:import (clojure.core.async.impl.channels ManyToManyChannel)))

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
   {:help         {:coerce :boolean
                   :desc   "prints this info and exits"}
    :no-index     {:coerce  :boolean
                   :desc    "don't generate index page"
                   :default false}
    :add-exit     {:coerce  :boolean
                   :desc    "add /exit command"
                   :default false}
    :echo         {:coerce  :boolean
                   :default false
                   :desc    "reprints command output to stdout"}
    :trigger-only {:coerce  :boolean
                   :default false
                   :desc    "only command is executed and no output returned"}
    :port         {:coerce  :number
                   :default 8080
                   :desc    "port for http server (default 8080)"}}
   :coerce     {:urls []}
   :validate   {:urls args-ok?}
   :args->opts [:urls]})


(extend-type ManyToManyChannel
  StreamableResponseBody
  (write-body-to-stream [channel response output-stream]
    (async/go (with-open [^java.io.Writer writer (io/writer output-stream)]
                (loop []
                  (when-let [^String msg (async/<! channel)]
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

(defn execute-async! [command]
  (let [main-chan  (async/chan 1 (map #(str % "\n")) #(println "main-chan ex:" %))
        mx-chan    (async/mult main-chan)
        ret-chan   (async/chan 1024 (map #(str "OUT: " %)) #(println "ret-chan ex:" %))
        print-chan (async/chan 1 (map #(str "LOG: " %)) #(println "print-chan ex:" %))]

    (if (:trigger-only @options)
      (async/close! ret-chan)
      (async/tap mx-chan ret-chan))

    (when (:echo @options) (async/tap mx-chan print-chan))

    (async/go-loop []
      (let [line (async/<! print-chan)]
        (if line
          (do (print line) (flush)
              (recur))
          (println "out of print-chan loop..."))))

    (async/onto-chan! main-chan (line-seq (io/reader (:out (process/process command {:err :out})))))
    ret-chan))


(defn async-handler [request respond raise]
  (let [uri     (:uri request)
        command (get (:urls @options) uri)]
    (if command
      (respond {:status 200 :headers {} :body (execute-async! command)})

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
    (println "Server started")
    (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable stop-jetty-server))))

(defn set-args! [args]
  (reset! options
          (-> args
              (cli/parse-args cli-spec)
              :opts
              (update :urls parse-urls))))

(defn -main
  [& args]
  (let [opts (set-args! args)]
    (prn :opts opts)
    (if (or (:help opts) (:h opts))
      (println (show-help cli-spec))
      (start-jetty-server {:port (:port opts)}))))

(comment
  (set-args! ["--echo" "/ls" "ls" "/py" "python slow_log.py"])
  @options
  (:urls @options)

  (start-jetty-server {:port 3000})
  (stop-jetty-server)


  )
