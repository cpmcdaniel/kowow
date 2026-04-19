(ns kowow.tsm
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json])
  (:import [java.net URLEncoder]))

(def api-key "6D6czgYUzauVc8Zzp1MrTO61Wk84uNVP")

(def base-url "http://api.tradeskillmaster.com/v1/item/")

(defn with-params
  "Adds querystring params to the url"
  [url]
  (str url "?format=json&apiKey=" api-key))

(defn item
  "Gets aggregate item stats (global)"
  [item-id]
  (update 
   @(http/get
     (with-params
       (str base-url item-id)))
   :body
   json/parse-string))

(defn region-items
  "Gets stats for every item in a region"
  [region]
  {:pre [(#{"US" "EU"} region)]}
  (update
   @(http/get
     (with-params
       (str base-url "region/" region)))
   :body
   json/parse-string))

(defn realm-items
  "Gets stats for every item on a specific realm"
  [region realm]
  {:pre [#{"US" "EU"} region]}
  (update
   @(http/get
     (with-params
       (str base-url region "/"
            (URLEncoder/encode realm))))
   :body
   json/parse-string))

(def realm-items-cached (memoize realm-items))

(->>
 (:body (realm-items-cached "US" "Elune"))
 (filter #(= 1 (get % "Quantity")))
 (filter #(= "Armor" (get % "Class")))
 (filter #(not= "Miscellaneous" (get % "SubClass")))
 (filter #(< 0.1 (get % "RegionSaleRate")))
 (filter #(< 20000000 (get % "MinBuyout") (get % "MarketValue") (* 0.6 (get % "RegionMarketAvg"))))
 (filter #(< (get % "MinBuyout" "RegionSaleAvg")))
 (take 5))

