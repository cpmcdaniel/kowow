(ns kowow.blizzard
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json])
  (:import [java.util Base64]))

(def token-url "https://oauth.battle.net/token")
(def api-base "https://us.api.blizzard.com")

(defn- basic-auth-header
  "Builds a Basic Auth header from client-id and client-secret."
  [client-id client-secret]
  (let [credentials (str client-id ":" client-secret)
        encoded (.encodeToString (Base64/getEncoder) (.getBytes credentials "UTF-8"))]
    (str "Basic " encoded)))

(defn get-access-token
  "Retrieves an OAuth2 access token using the client credentials flow.
   Reads BLIZZARD_CLIENT_ID and BLIZZARD_CLIENT_SECRET from env."
  []
  (let [client-id (System/getenv "BLIZZARD_CLIENT_ID")
        client-secret (System/getenv "BLIZZARD_CLIENT_SECRET")
        _ (when (or (empty? client-id) (empty? client-secret))
            (throw (ex-info "Missing BLIZZARD_CLIENT_ID or BLIZZARD_CLIENT_SECRET env vars" {})))
        resp @(http/post token-url
                         {:headers {"Authorization" (basic-auth-header client-id client-secret)
                                    "Content-Type" "application/x-www-form-urlencoded"}
                          :body "grant_type=client_credentials"})
        body (json/parse-string (:body resp) true)]
    (if (:access_token body)
      (:access_token body)
      (throw (ex-info "Failed to retrieve access token" {:response body})))))

(defn wow-api-get
  "Makes an authenticated GET request to a Blizzard WoW API endpoint.
   Returns the parsed JSON response body."
  [token endpoint query-params]
  (let [url (str api-base endpoint)
        resp @(http/get url
                        {:headers {"Authorization" (str "Bearer " token)}
                         :query-params query-params})
        body (json/parse-string (:body resp) true)]
    body))

(defn wow-api-get-with-headers
  "Like `wow-api-get` but returns {:body <parsed> :headers <map>}."
  [token endpoint query-params]
  (let [url (str api-base endpoint)
        resp @(http/get url
                        {:headers {"Authorization" (str "Bearer " token)}
                         :query-params query-params})
        body (json/parse-string (:body resp) true)]
    {:body body
     :headers (:headers resp)}))

(comment
  (get-access-token)
  (let [token (get-access-token)]
    (wow-api-get token "/data/wow/realm/index"
                 {:namespace "dynamic-us"
                  :locale "en_US"}))
  :rcf)
