(ns network-monitor.handler
    (:require  
            [clojure.core.async :refer [<!! go timeout]]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [config.core :refer [env]]
            [hiccup.page :refer [include-js include-css html5]]
            [network_monitor.middleware :refer [wrap-middleware]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :refer [response]]
    )
)

(import (java.net InetAddress))
(defonce state (atom {:machines [] :last_sweep 0}))
(defonce ip_range "192.168.0")

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

; in place of using (sh "ping"), which tries to ping
; until you stop it
(defn ping [host]
    (.isReachable (InetAddress/getByName host) 2000)
)

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(defn loading-page []
  (html5
    (head)
    [:body {:class "body-container"}
     mount-target
     (include-js "/js/app.js")]))

(defn machines []
    (wrap-json-response 
        (fn [_] 
            (let [machines @state]
                (response machines)
            )
        )
    )
)

; get each {ip mac} pair with given ip_prefix
; from the given string
(defn extract_machine_map [strn ip_prefix]
    (let
        [
            digits [0 1 2 3 4 5 6 7 8 9]
            lines (str/split-lines strn)
            ips (map #(let [end (str/index-of % ")")]
                        (subs % 3 end)
                      )
                      lines
                )
            macs (map #(let [start (+ (str/index-of % "at ") 3)
                             end (str/index-of % " on")]
                        (subs % start end)
                       )
                       lines
                 )
            machines (map (fn [x y] {:ip x :mac y}) ips macs)
            machines (filter
                        (fn [machine]
                              (and
                                (.contains (:ip machine) ip_prefix)
                                (reduce
                                    (fn [x y]
                                        (or x y)
                                    )
                                    (for [i digits]
                                        (.contains
                                            (:mac machine)
                                            (str i)
                                        )
                                    )
                                )
                              )
                        )
                        machines
                     )
        ]
        machines
    )
)

; run arp -a and return the stdout
(defn read_arp_cache []
    (let
        [
            res (sh "arp" "-a")
            output (:out res)
        ]
        output
    )
)

; read arp cache and put the result (parsed) into @state
(defn update_state_from_arp_cache []
    (let [
            raw (read_arp_cache)
            m_map (extract_machine_map raw ip_range)
         ]
        (swap! state assoc-in [:machines] m_map)
    )
)

; ping all machines on a given c-class network, then update
; our state atom based on the contents of the arp cache
(defn ping_sweep [base]
    (for [x (range 256)]
        (let [address (str base "." x)]
            (go (do
                (ping address)
            ))
        )
    )
)

;TODO I don't know why but the ping/arp thing works if they're
; two separate calls instead of one with (do). So make the client
; have timers to set off the sweep, read cache, and pull machines.
; Probabyl keep timestamp on ping and arp to keep them from being
; spammed by multiple machines
(defroutes routes
  (GET "/" [] (loading-page))
  (GET "/ping" [] (ping_sweep ip_range)) 
  (GET "/update" [] (update_state_from_arp_cache)) 
  (GET "/machines" [] (machines))
  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
