(ns kowow.wow
  (:require [kowow.blizzard :as blizzard]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(defn fetch-realm-index
  [token]
  (blizzard/wow-api-get token "/data/wow/realm/index"
                       {:namespace "dynamic-us"
                        :locale "en_US"}))

(defn fetch-connected-realm-index
  [token]
  (blizzard/wow-api-get token "/data/wow/connected-realm/index"
                       {:namespace "dynamic-us"
                        :locale "en_US"}))

(defn fetch-auctions-for-connected-realm
  [token connected-realm-id]
  (blizzard/wow-api-get token (str "/data/wow/connected-realm/" connected-realm-id "/auctions")
                       {:namespace "dynamic-us"
                        :locale "en_US"}))

(defn fetch-connected-realm
  [token id]
  (blizzard/wow-api-get token (str "/data/wow/connected-realm/" id)
                        {:namespace "dynamic-us"
                         :locale "en_US"}))

(defn connected-realm-id-from-href
  "Parse a connected-realm id integer from a `:href` string returned by the API.
   Returns nil if it cannot be parsed." 
  [href]
  (when href
    (let [m (re-find #".*/connected-realm/(\d+)" href)]
      (when m
        (Integer/parseInt (second m))))))

(defn connected-realm-label
  "Return the slug of the first realm in a connected-realm resource, or nil."
  [cr-res]
  (-> cr-res :realms first :slug))

(defn fetch-all-auctions
  "Fetch auctions for every connected realm and write each snapshot to a labeled JSON file.
   Options: :output-dir (string) and :throttle-ms (milliseconds between requests)."
  [token & {:keys [output-dir throttle-ms]
            :or {output-dir "output/auctions" throttle-ms 200}}]
  (let [idx (fetch-connected-realm-index token)
        ids  (->> (:connected_realms idx)
                  (keep (comp connected-realm-id-from-href :href)))]
    (.mkdirs (io/file output-dir))
    (doseq [id ids]
      (Thread/sleep throttle-ms)
      (try
        (let [cr (fetch-connected-realm token id)
              label (connected-realm-label cr)
              auctions (fetch-auctions-for-connected-realm token id)
              fname (if label
                      (format "auctions-%s-%d.json" label id)
                      (format "auctions-%d.json" id))
              out-file (io/file output-dir fname)
              pretty (json/generate-string auctions {:pretty true})]
          (spit out-file pretty)
          (println "Wrote" (.getPath out-file)))
        (catch Exception e
          (binding [*out* *err*]
            (println "Failed for connected-realm" id ":" (.getMessage e))))))))

(defn- body-hash
  "Return a hex SHA-256 digest of a string."
  [^String s]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        digest (.digest md (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" %) digest))))

(defn poll-auctions
  "Poll the Elune (connected-realm 67) auctions endpoint every `interval-ms`
   (default 10 min) and print whether the snapshot changed.
   Runs `n` iterations (default 7 ≈ 1 hour).  Returns the collected log entries.
   Stop early by interrupting the thread."
  [token & {:keys [connected-realm-id interval-ms n]
            :or {connected-realm-id 67
                 interval-ms (* 10 60 1000)
                 n 7}}]
  (let [endpoint (str "/data/wow/connected-realm/" connected-realm-id "/auctions")
        params {:namespace "dynamic-us" :locale "en_US"}]
    (loop [i 0
           prev-hash nil
           prev-last-modified nil
           log []]
      (if (>= i n)
        log
        (let [{:keys [body headers]} (blizzard/wow-api-get-with-headers token endpoint params)
              json-str (json/generate-string body)
              hash (body-hash json-str)
              last-mod (:last-modified headers)
              now (java.time.Instant/now)
              changed? (and prev-hash (not= hash prev-hash))
              entry {:poll i
                     :time (str now)
                     :auction-count (count (:auctions body))
                     :hash (subs hash 0 12)
                     :hash-changed? (if prev-hash changed? :first-poll)
                     :last-modified last-mod
                     :last-modified-changed? (if prev-last-modified
                                               (not= last-mod prev-last-modified)
                                               :first-poll)}]
          (println (format "[%d] %s | auctions=%d | hash=%s changed=%s | Last-Modified=%s changed=%s"
                           i now
                           (count (:auctions body))
                           (subs hash 0 12)
                           (:hash-changed? entry)
                           last-mod
                           (:last-modified-changed? entry)))
          (let [new-log (conj log entry)]
            (if (>= (inc i) n)
              new-log
              (do (Thread/sleep interval-ms)
                  (recur (inc i) hash last-mod new-log)))))))))

(defn -main
  [& _args]
  (println "Fetching auctions for all connected realms...")
  (let [token (blizzard/get-access-token)]
    (fetch-all-auctions token)
    (println "Done.")))

(defn character-professions
  [token realm character-name]
  (blizzard/wow-api-get token (str "/profile/wow/character/" realm "/" character-name "/professions")
                       {:namespace "profile-us"
                        :locale "en_US"}))

(comment
  (def token (blizzard/get-access-token))
  (fetch-connected-realm-index token)
  (character-professions token "elune" "kowmann")

  ;; Quick test: 3 polls, 30 s apart
  (poll-auctions token :n 3 :interval-ms 30000)

  ;; Full run: 7 polls, 10 min apart (~1 hour)
  (def poll-log (poll-auctions token))

  :rcf)