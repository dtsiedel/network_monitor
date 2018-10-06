(ns network-monitor.handler
    (:require  
            [clojure.core.async :refer [<!! go timeout]]
            [clojure.java.shell :refer [sh]]
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
            (let [machines_string @state]
                (response {:test machines_string})
            )
        )
    )
)

; repeatedly call a function every *ms* milliseconds
(defn set_interval [callback ms]
    (future (while true (do (Thread/sleep ms) (callback))))
)

; ping all machines on given class C network
(defn ping_sweep [first_octet]
    (for [last_octet (range 265)]
        (let [name (str first_octet "." last_octet)]
            (sh "ping" name)
        )
    )
)

; run arp -a and parse the results
;TODO: parse the results
(defn read_arp_cache []
    (let
        [
            res (sh "arp" "-a")
            output (:out res)
        ]
        output
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
                (ping_sweep "192.168.0")
                (let [m (read_arp_cache)]
                    (swap! machines assoc-in [:machines] m)
                )
            )
            (reset! state {:machines @machines})
        )
    )
)

