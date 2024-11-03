(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'shell2http_clj)
(def version (format "0.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uberjar [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis      @basis
                  :ns-compile '[shell2http-clj.main]
                  :class-dir  class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     @basis
           :main      'shell2http-clj.main}))
