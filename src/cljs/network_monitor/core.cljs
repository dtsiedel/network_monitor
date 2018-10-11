(ns network-monitor.core
    (:require
              [ajax.core :as ajax]
              [clojure.walk :as walk]
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
        :id (str (random-uuid))
    }
)

;;;;
;;;; reframe query functions
;;;;

; register the query function, so that later you can get it with
; (subscribe :machines). This gives you the reactive version of
; that view of the database
(reframe/reg-sub
    :machines
    (fn [db v]
        (:machines db)
    )
)

; subscription for the query list
(reframe/reg-sub
    :trusted
    (fn [db v]
        (:trusted db)
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
        {:machines [] :trusted []}
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
        (let [
              db (:db cofx)
              machines (second event)
              keywordized (walk/keywordize-keys machines)
              machines_list (:machines keywordized)
             ]
            {
                :db (assoc-in db [:machines] machines_list)
            }
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

; new db-state: add ip if it was absent, remove if it was present
(reframe/reg-event-fx
    :toggle_trusted
    (fn [cofx event]
        (let [
              db (:db cofx)
              ip (second event)
             ]
            {:db (assoc-in
                    db
                    [:trusted]
                    (let [trusted (:trusted db)]
                        (if (not (some #{ip} trusted))
                            (conj trusted ip)
                            (remove #(= % ip) trusted)
                        )
                    )
                )
            }
        )
    )
)

;;;;
;;;; reagent components
;;;;

; the top bar
(defn title []
    [:div {:class "title"} 
        [:div "Network Monitor"]
        [:button {:on-click #(reframe/dispatch [:fetch-machines])}
                "Update Machines"
        ]
    ]
)

; a single machine's representation
(defn machine [m]
    (let [
          trusted (reframe/subscribe [:trusted])
          base_class "machine"
          ip (:ip m)
          mac (:mac m)
         ]
        [:div
            {:class (if (some #{ip} @trusted)
                        (str base_class " trusted")
                        (str base_class " untrusted")
                    )
            }
            [:div (str "IP: " ip)]
            [:div (str "MAC: " mac)]
            [:button
                {:on-click
                    #(do
                        (reframe/dispatch [:toggle_trusted (:ip m)])
                     )
                }
                "Toggle Trusted"
            ]
        ]
    )
)

; a container for all [machine]s
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
