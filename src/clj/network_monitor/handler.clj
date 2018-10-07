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

(defonce state (atom {:machines []}))
(defonce ip_range "192.168.0")

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

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

; ping all machines on given class C network
(defn ping_sweep [first_octet]
    (for [last_octet (range 265)]
        (let [name (str first_octet "." last_octet)]
            (sh "ping" name)
        )
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

; get each {ip mac} pair with given ip_prefix
; from the given string
(defn extract_machine_map [strn ip_prefix]
    (let
        [
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
            machines (map (fn [x y] {:ip x :machine y}) ips macs)
            machines (filter
                        (fn [machine]
                              (.contains
                                (:ip machine)
                                ip_prefix
                              )
                        )
                        machines
                     )
        ]
        machines
    )
)

(defroutes routes
  (GET "/" [] (loading-page))
  (GET "/machines" [] (machines))
  
  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))

; set a timer to run our ping sweep and arp check
; periodically. Had to do some tricky stuff with atoms
; to allow @state to be updated from within the go block
(go
    (let [machines (atom {:machines []})] 
        (while true
            (<!! (timeout 5000))
            (do
                (ping_sweep ip_range)
                (let [
                        raw (read_arp_cache)
                        m_map (extract_machine_map raw ip_range)
                     ]
                    (swap! machines assoc-in [:machines] m_map)
                )
            )
            (reset! state {:machines (:machines @machines)})
        )
    )
)

