(ns cmr.system-int-test.utils.ingest-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.umm.echo10.collection :as c]
            [cmr.umm.echo10.granule :as g]
            [cmr.system-int-test.data2.provider-holdings :as ph]
            [cmr.umm.echo10.core :as echo10]
            [cmr.acl.core :as acl]
            [cmr.transmit.config :as transmit-config]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.echo-util :as echo-util]
            [cmr.common.util :as util]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(defn- create-provider-through-url
  "Create the provider by http POST on the given url"
  [provider-id endpoint-url]
  (client/post endpoint-url
               {:body (format "{\"provider-id\": \"%s\"}" provider-id)
                :content-type :json
                :connection-manager (s/conn-mgr)
                :headers {acl/token-header (transmit-config/echo-system-token)}}))

(defn create-mdb-provider
  "Create the provider with the given provider id in the metadata db"
  [provider-id]
  (create-provider-through-url provider-id (url/create-provider-url)))

(defn create-ingest-provider
  "Create the provider with the given provider id through ingest app"
  [provider-id]
  (create-provider-through-url provider-id (url/ingest-create-provider-url)))

(defn get-providers-through-url
  [provider-url]
  (-> (client/get provider-url {:connection-manager (s/conn-mgr)})
      :body
      (json/decode true)
      :providers))

(defn get-providers
  []
  (get-providers-through-url (url/create-provider-url)))

(defn get-ingest-providers
  []
  (get-providers-through-url (url/ingest-create-provider-url)))

(defn delete-provider
  "Delete the provider with the matching provider-id from the CMR metadata repo."
  [provider-id]
  (let [response (client/delete (url/delete-provider-url provider-id)
                                {:throw-exceptions false
                                 :connection-manager (s/conn-mgr)
                                 :headers {acl/token-header (transmit-config/echo-system-token)}})
        status (:status response)]
    (is (some #{200 404} [status]))))

(defn delete-ingest-provider
  "Delete the provider with the matching provider-id through the CMR ingest app."
  [provider-id]
  (let [response (client/delete (url/ingest-delete-provider-url provider-id)
                                {:throw-exceptions false
                                 :connection-manager (s/conn-mgr)
                                 :headers {acl/token-header (transmit-config/echo-system-token)}})]
    (:status response)))

(defn reindex-collection-permitted-groups
  "Tells ingest to run the reindex-collection-permitted-groups job"
  []
  (let [response (client/post (url/reindex-collection-permitted-groups-url))]
    (is (= 200 (:status response)))))

(defn cleanup-expired-collections
  "Tells ingest to run the cleanup-expired-collections job"
  []
  (let [response (client/post (url/cleanup-expired-collections-url))]
    (is (= 200 (:status response)))))

(defn ingest-concept
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  ([concept]
   (ingest-concept concept {}))
  ([concept options]
   (let [{:keys [metadata format concept-type concept-id revision-id provider-id native-id]} concept
         token (:token options)
         accept-format (get options :accept :json)
         headers (util/remove-nil-keys {"concept-id" concept-id
                                        "revision-id" revision-id
                                        "Echo-Token" token})
         response (client/request
                    {:method :put
                     :url (url/ingest-url provider-id concept-type native-id)
                     :body  metadata
                     :content-type format
                     :headers headers
                     :accept accept-format
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn multipart-param-request
  "Submits a multipart parameter request to the given url with the multipart parameters indicated.
  A multipart parameter is a map the keys :name, :content, and :content-type."
  [url multipart-params]

  ;; clj-http has a specific format for the multipart params.
  (let [multipart-params (for [{:keys [name content content-type]} multipart-params]
                           {:name name
                            :content content
                            :mime-type content-type
                            :encoding "UTF-8"})
        response (client/request {:method :post
                                  :url url
                                  :multipart multipart-params
                                  :accept :json
                                  :throw-exceptions false
                                  :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn validate-concept
  "Validate a concept and return a map with status and error messages if applicable."
  [{:keys [metadata format concept-type concept-id revision-id provider-id native-id] :as concept}]
  (let [headers (util/remove-nil-keys {"concept-id" concept-id "revision-id" revision-id})
        response (client/request
                   {:method :post
                    :url (url/validate-url provider-id concept-type native-id)
                    :body  metadata
                    :content-type format
                    :headers headers
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn validate-granule
  "Validates a granule concept by sending it and optionally its parent collection to the validation
  endpoint"
  ([granule-concept]
   ;; Use the regular validation endpoint in this case.
   (validate-concept granule-concept))
  ([granule-concept collection-concept]
   (let [{:keys [provider-id native-id]} granule-concept]
     (multipart-param-request
       (url/validate-url provider-id :granule native-id)
       [{:name "granule"
         :content (:metadata granule-concept)
         :content-type (:format granule-concept)}
        {:name "collection"
         :content (:metadata collection-concept)
         :content-type (:format collection-concept)}]))))

(defn save-concept
  "Save a concept to the metadata db and return a map with status, concept-id, and revision-id"
  [concept]
  (let [response (client/request
                   {:method :post
                    :url (url/mdb-concepts-url)
                    :body  (json/generate-string concept)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn provider-holdings
  "Returns the provider holdings from metadata db."
  []
  (let [url (url/mdb-provider-holdings-url)
        response (client/get url {:accept :json
                                  :connection-manager (s/conn-mgr)})
        {:keys [status body headers]} response]
    (if (= status 200)
      {:status status
       :headers headers
       :results (ph/parse-provider-holdings :json false body)}
      response)))

(defn tombstone-concept
  "Create a tombstone in mdb for the concept, but don't delete it from elastic."
  [concept]
  (let [{:keys [concept-id revision-id]} concept
        response (client/request
                   {:method :delete
                    :url (str (url/mdb-concepts-url) "/" concept-id "/" revision-id)
                    :body  (json/generate-string concept)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn delete-concept
  "Delete a given concept."
  ([concept]
   (delete-concept concept nil))
  ([{:keys [provider-id concept-type native-id] :as concept} token]
   (let [response (client/request
                    {:method :delete
                     :url (url/ingest-url provider-id concept-type native-id)
                     :headers (merge {} (when token {"Echo-Token" token}))
                     :accept :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))


(defn ingest-concepts
  "Ingests all the given concepts assuming that they should all be successful."
  [concepts]
  (doseq [concept concepts]
    (is (= {:status 200
            :concept-id (:concept-id concept)
            :revision-id (:revision-id concept)}
           (ingest-concept concept)))))

(defn delete-concepts
  "Deletes all the given concepts assuming that they should all be successful."
  [concepts]
  (doseq [concept concepts]
    (is (#{404 200} (:status (delete-concept concept))))))

(defn get-concept
  ([concept-id]
   (get-concept concept-id nil))
  ([concept-id revision-id]
   (let [response (client/get (url/mdb-concept-url concept-id revision-id)
                              {:accept :json
                               :throw-exceptions false
                               :connection-manager (s/conn-mgr)})]
     (is (some #{200 404} [(:status response)]))
     (when (= (:status response) 200)
       (-> response
           :body
           (json/decode true)
           (update-in [:concept-type] keyword))))))

(defn concept-exists-in-mdb?
  "Check concept in mdb with the given concept and revision-id"
  [concept-id revision-id]
  (not (nil? (get-concept concept-id revision-id))))

;;; fixture - each test to call this fixture
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-provider
  ([provider-guid provider-id]
   (create-provider provider-guid provider-id true))
  ([provider-guid provider-id grant-all-search?]
   (create-provider provider-guid provider-id grant-all-search? true))
  ([provider-guid provider-id grant-all-search? grant-all-ingest?]
   (create-mdb-provider provider-id)
   (echo-util/create-providers {provider-guid provider-id})

   (when grant-all-search?
     (echo-util/grant [echo-util/guest-ace
                       echo-util/registered-user-ace]
                      (assoc (echo-util/catalog-item-id provider-guid)
                             :collection-applicable true
                             :granule-applicable true)
                      :system-object-identity
                      nil))
   (when grant-all-ingest?
     (echo-util/grant-all-ingest provider-guid))))

(defn reset-fixture
  "Creates the given providers in ECHO and the CMR then clears out all data at the end."
  ([]
   (reset-fixture {}))
  ([provider-guid-id-map]
   (reset-fixture provider-guid-id-map true))
  ([provider-guid-id-map grant-all-search?]
   (reset-fixture provider-guid-id-map grant-all-search? true))
  ([provider-guid-id-map grant-all-search? grant-all-ingest?]
   (fn [f]
     (dev-sys-util/reset)
     (doseq [[provider-guid provider-id] provider-guid-id-map]
       (create-provider provider-guid provider-id grant-all-search? grant-all-ingest?))
     (f))))
