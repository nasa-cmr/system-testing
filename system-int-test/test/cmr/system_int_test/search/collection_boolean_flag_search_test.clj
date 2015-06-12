(ns cmr.system-int-test.search.collection-boolean-flag-search-test
  "Integration tests for searching by downloadable and browsable"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-collection-by-downloadable
  (let [ru1 (dc/related-url {:type "GET DATA"})
        ru2 (dc/related-url {:type "GET RELATED VISUALIZATION"})
        ru3 (dc/related-url {:type "VIEW RELATED INFORMATION"})
        coll1 (d/ingest "PROV1" (dc/collection {:related-urls [ru1]}))
        coll2 (d/ingest "PROV1" (dc/collection {:related-urls [ru2]}))
        coll3 (d/ingest "PROV1" (dc/collection {:related-urls [ru3]}))
        coll4 (d/ingest "PROV1" (dc/collection {:related-urls [ru2 ru3]}))
        coll5 (d/ingest "PROV1" (dc/collection {:related-urls [ru1 ru2]}))
        coll6 (d/ingest "PROV1" (dc/collection {}))]

    (index/wait-until-indexed)

    (testing "search by downloadable flag."
      (are [items value]
         (d/refs-match? items (search/find-refs :collection {:downloadable value}))
         [coll1 coll5] true
         [coll2 coll3 coll4 coll6] false
         [coll1 coll2 coll3 coll4 coll5 coll6] "unset"))

    (testing "search by downloadable wrong value"
      (is (= {:status 400 :errors ["Parameter downloadable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :collection {:downloadable "wrong"}))))

    (testing "search by online only flag"
      (are [items value]
         (d/refs-match? items (search/find-refs :collection {:online-only value}))
         [coll1 coll5] true
         [coll2 coll3 coll4 coll6] false
         [coll1 coll2 coll3 coll4 coll5 coll6] "unset"))

     (testing "search by online only wrong value"
      (is (= {:status 400 :errors ["Parameter downloadable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :collection {:online-only "wrong"}))))

    (testing "search by online only with aql"
      (are [items value]
           (d/refs-match? items
                          (search/find-refs-with-aql :collection [{:onlineOnly value}]))
           ;; it is not possible to search onlineOnly false in AQL, so we don't have a test for that
           [coll1 coll5] true
           [coll1 coll5] nil))


    (testing "search by browsable flag."
      (are [items value]
         (d/refs-match? items (search/find-refs :collection {:browsable value}))
         [coll2 coll4 coll5] true
         [coll1 coll3 coll6] false
         [coll1 coll2 coll3 coll4 coll5 coll6] "unset"))

    (testing "search by browsable wrong value"
      (is (= {:status 400 :errors ["Parameter browsable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :collection {:browsable "wrong"}))))

    (testing "search by browse_only flag."
      (are [items value]
         (d/refs-match? items (search/find-refs :collection {:browse-only value}))
         [coll2 coll4 coll5] true
         [coll1 coll3 coll6] false
         [coll1 coll2 coll3 coll4 coll5 coll6] "unset"))

    (testing "search by browse_only wrong value"
      (is (= {:status 400 :errors ["Parameter browsable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :collection {:browse-only "wrong"}))))))
