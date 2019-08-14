(ns do-app-cljs.prod
  (:require [do-app-cljs.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
