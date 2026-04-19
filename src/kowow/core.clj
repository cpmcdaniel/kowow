(ns kowow.core
  (:require [kowow.blizzard :as blizzard]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(defn -main
  [& _args]
  (println "Authenticating with Blizzard OAuth2...")
  (let [token (blizzard/get-access-token)
        _ (println "Token acquired. Fetching WoW realm index...")
        realms (blizzard/wow-api-get token "/data/wow/realm/index"
                                     {:namespace "dynamic-us"
                                      :locale "en_US"})
        output-dir (io/file "output")
        output-file (io/file output-dir "wow-realms.json")
        pretty-json (json/generate-string realms {:pretty true})]
    (.mkdirs output-dir)
    (spit output-file pretty-json)
    (println (str "Success! Wrote " (count (:realms realms)) " realms to " (.getPath output-file)))))

(comment
  (-main)
  :rcf)
