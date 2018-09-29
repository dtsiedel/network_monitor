(ns network-monitor.core
    (:require [reagent.core :as reagent :refer [atom]]
              [re-frame.core :as reframe]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]))
;;;;
;;;; helper functions
;;;;

(defn random-two-hex []
    (let [acceptable "0123456789ABCDEF"
          characters (for [x (range 2)]
                        (rand-nth acceptable)
                     )
         ]
        (clojure.string/join "" characters)
    )
)

(defn random-octet []
    (str (rand-nth (range 256)))
)

(defn random-mac []
    (let [
            groups (for [x (range 6)]
                    (random-two-hex))
         ]
        (clojure.string/join ":" groups)                
    ) 
)

(defn random-ip []
    (let [
            groups (for [x (range 4)]
                    (random-octet)
                   )
         ]
        (clojure.string/join "." groups)
    )
)

(defn random-machine []
    {
        :mac (random-mac)
        :ip (random-ip)
        :timestamp (.getTime (js.Date.))
    }
)

;;;;
;;;; reframe query functions
;;;;

(defn query-machines 
    [db v]
    :machines
    (:machines db)
)

; register the query function, so that later you can get it with
; (subscribe :machines)
(reframe/reg-sub
    :machines
    query-machines
)

;do we have data yet?
(re-frame.core/reg-sub
  :initialized?
  (fn [db _]
    (not (empty? db))))

;;;;
;;;; reframe event handlers
;;;;

(defn handle-add-random
    [coeffects event]
    (let [db (:db coeffects)]
        ;return map describing effect
        {:db (update-in db [:machines] conj (random-machine))}
    )
)

;;;;
;;;; reframe registrations
;;;;

; give initial values to db. Return value is initial value of
; db
(reframe/reg-event-db
    :init-db
    (fn [_ _]
        {:machines []}
    )
)

(reframe/reg-event-fx
    :add-random
    handle-add-random
)

;;;;
;;;; reagent components
;;;;

(defn title []
    [:div {:class "title"} 
        [:div "Network Monitor"]
        [:button {:on-click #(reframe/dispatch [:add-random])}
                "Add random machine"
        ]
    ]
)
(defn machine [m]
    [:div 
        {:class "machine"}
        [:div (str "IP: " (:ip m))]
        [:div (str "MAC: " (:mac m))]
        [:div (str "Time: " (:timestamp m))]
    ]
)

(defn machines []
    ; re-render whenever query-machines changes
    (let [machines (reframe/subscribe [:machines])]
        [:div {:class "machines"}
            (for [m @machines]
                ^{:key m}
                [machine m] 
            )
        ]
    )
)

(defn page-root []
    (let [ready? (reframe/subscribe [:initialized?])]
        (if (not ready?)
            [:div "Loading..."]
            [:div {:class "root"}
                [title]
                [machines]
            ]
        )
    )
)

;; initialization code

(defn mount-root []
    (reframe/dispatch [:init-db])
    (reagent/render [page-root] 
        (.getElementById js/document "app")
    )
)

(defn init! []
    (mount-root)
)
