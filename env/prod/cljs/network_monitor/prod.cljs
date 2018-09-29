(ns network-monitor.prod
  (:require [network-monitor.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
