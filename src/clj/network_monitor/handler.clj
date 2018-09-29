(ns network-monitor.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [network_monitor.middleware :refer [wrap-middleware]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :refer [response]]
            [config.core :refer [env]]))

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

;TODO: actually get the machines lol
(defn machines []
    (wrap-json-response 
        (fn [_] 
            (response {:test "post"})
        )
    )
)

(defroutes routes
  (GET "/" [] (loading-page))
  (GET "/machines" [] (machines))
  
  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
