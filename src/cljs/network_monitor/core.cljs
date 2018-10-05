(ns network-monitor.core
    (:require
              [ajax.core :as ajax]
              [reagent.core :as reagent :refer [atom]]
              [re-frame.core :as reframe]
              [day8.re-frame.http-fx]
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
        :id (str (random-uuid))
    }
)

;;;;
;;;; reframe query functions
;;;;

; register the query function, so that later you can get it with
; (subscribe :machines)
(reframe/reg-sub
    :machines
    (fn [db v]
        (:machines db)
    )
)

;do we have data yet?
(re-frame.core/reg-sub
    :initialized?
    (fn [db _]
        (not (empty? db))
    )
)

;;;;
;;;; reframe registrations (maps events to handlers)
;;;;

(reframe/reg-event-db
    :init-db
    (fn [_ _]
        ;return is starting state of database
        {:machines []}
    )
)

(reframe/reg-event-fx
    :failed
    (fn [cofx event]
        (println "Failed http request" cofx event)
    )
)

(reframe/reg-event-fx
    :got-machines
    (fn [cofx event]
        (let [machines (second event)]
            ;TODO: parse and update db
            (println "Got machines " machines) 
        )
    )
)

(reframe/reg-event-fx
    :fetch-machines
    (fn [cofx event]
        {:http-xhrio 
            {
                :method :get
                :uri "/machines"
                :response-format (ajax/json-response-format)
                :on-success [:got-machines]
                :on-failure [:failed]
            }
        }
    )
)

(reframe/reg-event-fx
    :add-machine
    (fn[cofx event]
        (let [db (:db cofx)]
            ;return map describing effect
            ;in this case, new db state
            {:db (update-in db [:machines] conj (random-machine))}
        )
    )
)

(reframe/reg-event-fx
    :delete-machine
    (fn [cofx event]
        (let [
              db (:db cofx)
              id (second event)
             ]
            {:db (update-in
                    db
                    [:machines]
                    (fn [x] (remove #(= (:id %) id) x))
                 )
            }
        )
    )
)

;;;;
;;;; reagent components
;;;;

(defn title []
    [:div {:class "title"} 
        [:div "Network Monitor"]
        [:button {:on-click #(reframe/dispatch [:fetch-machines])}
                "Update Machines"
        ]
    ]
)
(defn machine [m]
    [:div 
        {:class "machine"}
        [:div (str "IP: " (:ip m))]
        [:div (str "MAC: " (:mac m))]
        [:div (str "Time: " (:timestamp m))]
        [:button 
            {:on-click
            #(reframe/dispatch [:delete-machine (:id m)])}
            "Delete"
        ]
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
