(ns cmr.system-int-test.ingest.provider-ingest-test
  "CMR provider ingest integration test"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest provider-ingest-test
  (testing "create provider and get providers through ingest app"
    (let [{:keys [status]} (ingest/create-ingest-provider "PROV3")]
      (is (= 201 status))
      (is (= (ingest/get-providers) (ingest/get-ingest-providers))))))

(deftest delete-provider-test
  (let [coll1 (d/ingest "PROV1" (dc/collection))
        gran1 (d/ingest "PROV1" (dg/granule coll1))
        gran2 (d/ingest "PROV1" (dg/granule coll1))
        coll2 (d/ingest "PROV1" (dc/collection))
        gran3 (d/ingest "PROV1" (dg/granule coll2))
        coll3 (d/ingest "PROV2" (dc/collection))
        gran4 (d/ingest "PROV2" (dg/granule coll3))]
    (index/refresh-elastic-index)

    (is (= 2 (count (:refs (search/find-refs :collection {:provider-id "PROV1"})))))
    (is (= 3 (count (:refs (search/find-refs :granule {:provider-id "PROV1"})))))

    ;; delete provider PROV1
    (is (= 200 (ingest/delete-ingest-provider "PROV1")))
    (index/refresh-elastic-index)

    ;; PROV1 concepts are not in metadata-db
    (are [concept]
         (not (ingest/concept-exists-in-mdb? (:concept-id concept) (:revision-id concept)))
         coll1
         coll2
         gran1
         gran2
         gran3)

    ;; PROV2 concepts are in metadata-db
    (are [concept]
         (ingest/concept-exists-in-mdb? (:concept-id concept) (:revision-id concept))
         coll3
         gran4)

    ;; search on PROV1 finds nothing
    (is (d/refs-match?
          []
          (search/find-refs :collection {:provider-id "PROV1"})))
    (is (d/refs-match?
          []
          (search/find-refs :granule {:provider-id "PROV1"})))

    ;; search on PROV2 finds the concepts
    (is (d/refs-match?
          [coll3]
          (search/find-refs :collection {:provider-id "PROV2"})))
    (is (d/refs-match?
          [gran4]
          (search/find-refs :granule {:provider-id "PROV2"})))))

(deftest delete-non-existent-provider-test
  (is (= 404 (ingest/delete-ingest-provider "NonExistentProvider"))))
