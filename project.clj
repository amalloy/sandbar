(defproject sandbar/sandbar "0.4.0-SNAPSHOT"
  :description "Clojure web application libraries built on top of Ring
                and Compojure."
  :url "http://github.com/brentonashworth/sandbar"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [ring/ring-core "1.0.1"]
                 [compojure "0.6.5"]
                 [hiccup "0.3.6"]
                 [slingshot "0.10.0"]
                 [org.clojure/tools.macro "0.1.1"]]
  :dev-dependencies [[jline "0.9.94"]
                     [ring/ring-devel "0.3.7"]
                     [ring/ring-jetty-adapter "0.3.7"]
                     [ring/ring-httpcore-adapter "0.3.5"]
                     [lein-difftest "1.3.2-SNAPSHOT"]
                     [radagast "1.0.0"]
                     [enlive "1.0.0"]
                     [marginalia "0.5.0"]]
  :hooks [leiningen.hooks.difftest]
  :radagast/ns-whitelist #"^sandbar.*")
