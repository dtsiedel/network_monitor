(ns ^:figwheel-no-load network-monitor.dev
  (:require
    [network-monitor.core :as core]
    [devtools.core :as devtools]))

(devtools/install!)

(enable-console-print!)

(core/init!)
