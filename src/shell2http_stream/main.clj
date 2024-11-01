(ns shell2http-stream.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [babashka.process :as process]
            [babashka.cli :as cli]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.core.protocols :refer [StreamableResponseBody]]
            [clojure.core.async :as async]
            [hiccup2.core :as h]
            [ring.util.codec :as codec])
  (:import (clojure.core.async.impl.channels ManyToManyChannel)))

(set! *warn-on-reflection* true)


(defn args-ok? [args]
  (and (< 1 (count args))
       (even? (count args))
       (every? #(= \/ (ffirst %)) (partition 2 args))))

(defn parse-urls [urls]
  (apply hash-map urls))

(defn print-help [executable spec]
  (println "Usage:" executable "[options] /path \"shell command\" /path2 \"shell command2\"")
  (println "options:")
  (println (cli/format-opts (merge spec {:order (vec (keys (:spec spec)))}))))

(defn wrap-options [options handler]
  (fn [request respond raise]
    (handler (assoc request ::options options) respond raise)))


(def cli-spec
  {:spec
   {:help         {:coerce :boolean
                   :desc   "Prints this info and exits"}
    :no-index     {:coerce  :boolean
                   :desc    "Do not generate an index page"
                   :default false}
    :add-exit     {:coerce  :boolean
                   :desc    "Adds an /exit command"
                   :default false}
    :echo         {:coerce  :boolean
                   :default false
                   :desc    "Reprints command output to stdout"}
    :trigger-only {:coerce  :boolean
                   :default false
                   :desc    "Only executes the command without returning output"}
    :form         {:coerce  :boolean
                   :default false
                   :desc    "Populates environment variables from query parameters"}
    :host         {:default "0.0.0.0"
                   :desc    "The hostname to listen on (default: 0.0.0.0)"}
    :port         {:coerce  :number
                   :default 8080
                   :desc    "The port to listen on (default: 8080)"}}
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

(defn index-handler [{::keys [options]}]
  (let [urls (:urls options)]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body
     (str
      (h/html
       (into [:table]
             (for [[url command] urls]
               [:tr [:td [:a {:href url} url]] [:td command]]))))}))


(declare stop-jetty-server)

(defn main-handler [{::keys [options] :as request}]
  (let [uri (:uri request)]
    (cond
      (and (not (:no-index options)) (= uri  "/"))
      (index-handler request)

      (and (:add-exit options) (= uri "/exit"))
      (System/exit 0)

      :else
      {:status  404
       :headers {"Content-Type" "text/html"}
       :body    "Not found"})))

(defn execute-async! [options command envs]
  (let [main-chan  (async/chan 1 (map #(str % "\n")))
        mx-chan    (async/mult main-chan)
        ret-chan   (async/chan 1024)
        print-chan (async/chan 1)]

    (if (:trigger-only options)
      (async/close! ret-chan)
      (async/tap mx-chan ret-chan))

    (when (:echo options) (async/tap mx-chan print-chan))

    (async/go-loop []
      (let [line (async/<! print-chan)]
        (when line
          (print line) (flush)
          (recur))))

    (async/onto-chan! main-chan (line-seq (io/reader (:out (process/process command {:err :out :extra-env envs})))))
    ret-chan))


(defn async-handler [request respond raise]
  (let [options (::options request)
        uri     (:uri request)
        command (get (:urls options) uri)
        envs    (when (:form options)
                  (-> request :query-string (or "") codec/form-decode (as-> $ (into {} $))))]
    (if command
      (respond {:status 200
                :headers
                {"Content-Type"  "text/event-stream"
                 "Cache-Control" "no-cache"
                 "Connection"    "keep-alive"}
                :body   (execute-async! options command envs)})

      (respond (main-handler request)))))

(defonce jetty-server (atom {:instance nil
                             :config   nil}))

(defn stop-jetty-server []
  (println "Stopping server...")
  (.stop ^org.eclipse.jetty.server.Server (:instance @jetty-server)))

(defn start-jetty-server [& [config]]
  (prn config)
  (let [config (merge {:join? false
                       :async? true} config)
        jetty-config (select-keys config [:host :port :join? :async?])]
    (println (str "Starting server on " (:host jetty-config) ":" (:port jetty-config)))
    (swap! jetty-server assoc :instance (run-jetty (wrap-options config async-handler) jetty-config)
           :config config)
    (println "Server started")
    (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable stop-jetty-server))))

(defn options-from-args [args]
  (-> args
      (cli/parse-args cli-spec)
      :opts
      (update :urls parse-urls)))

(defn -main
  [& args]
  (let [options (options-from-args args)]
    (if (or (:help options) (:h options))
      (print-help "shell2http-stream" cli-spec)
      (start-jetty-server options))))

(comment

  (let [options (options-from-args ["--form" "--echo" "/ls" "ls" "/env" "env" "/echo" "echo Hello!"])]
    (start-jetty-server options))

  (stop-jetty-server)

;;
  )
