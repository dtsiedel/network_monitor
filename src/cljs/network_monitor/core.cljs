(ns network-monitor.core
    (:require [reagent.core :as reagent :refer [atom]]
              [re-frame.core :as reframe]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]))

(defn title []
    [:div {:class "title"} "Network Monitor"] 
)

(defn machines []
    [:div {:class "machines"} "The content"]
)

(defn page-root []
    [:div {:class "root"}
        [title]
        [machines]
    ]
)

(defn mount-root []
    (reagent/render [page-root] 
        (.getElementById js/document "app")
    )
)

(defn init! []
  (mount-root)
)
