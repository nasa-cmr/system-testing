(ns cmr.system-int-test.search.facets.collection-facets-v2-search-test
  "This tests retrieving v2 facets when searching for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.search.facets.facets-util :as fu]
            [cmr.system-int-test.search.facets.facet-responses :as fr]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def sk1 (dc/science-keyword {:category "Cat1"
                              :topic "Topic1"
                              :term "Term1"
                              :variable-level-1 "Level1-1"
                              :variable-level-2 "Level1-2"
                              :variable-level-3 "Level1-3"
                              :detailed-variable "Detail1"}))

(def sk2 (dc/science-keyword {:category "Hurricane"
                              :topic "Popular"
                              :term "Extreme"
                              :variable-level-1 "Level2-1"
                              :variable-level-2 "Level2-2"
                              :variable-level-3 "Level2-3"
                              :detailed-variable "UNIVERSAL"}))

(def sk3 (dc/science-keyword {:category "Hurricane"
                              :topic "Popular"
                              :term "UNIVERSAL"}))

(defn- search-and-return-v2-facets
  "Returns the facets returned by a search requesting v2 facets."
  ([]
   (search-and-return-v2-facets {}))
  ([search-params]
   (index/wait-until-indexed)
   (let [query-params (merge search-params {:page-size 0 :include-facets "v2"})]
     (get-in (search/find-concepts-json :collection query-params)
             [:results :facets]))))

(deftest all-facets-v2-test
  (fu/make-coll 1 "PROV1"
                (fu/science-keywords sk1 sk2)
                (fu/projects "proj1" "PROJ2")
                (fu/platforms fu/FROM_KMS 2 2 1)
                (fu/processing-level-id "PL1")
                {:organizations [(dc/org :archive-center "DOI/USGS/CMG/WHSC")]})
  (fu/make-coll 2 "PROV1"
                (fu/science-keywords sk1 sk3)
                (fu/projects "proj1" "PROJ2")
                (fu/platforms fu/FROM_KMS 2 2 1)
                (fu/processing-level-id "PL1")
                {:organizations [(dc/org :archive-center "DOI/USGS/CMG/WHSC")]})
  (is (= fr/expected-v2-facets-apply-links (search-and-return-v2-facets)))
  (testing "All fields applied for all facets"
    (let [search-params {:science-keywords {:0 {:category "Cat1"
                                                :topic "Topic1"
                                                :term "Term1"
                                                :variable-level-1 "Level1-1"
                                                :variable-level-2 "Level1-2"
                                                :variable-level-3 "Level1-3"}}
                         :project ["proj1"]
                         :platform ["DIADEM-1D"]
                         :instrument ["ATM"]
                         :processing-level-id ["PL1"]
                         :data-center "DOI/USGS/CMG/WHSC"}]
      (is (= fr/expected-v2-facets-remove-links (search-and-return-v2-facets search-params))))))

(deftest empty-hierarchical-facets-test
  (let [expected-empty-facets {:title "Browse Collections"
                               :type "group"
                               :has_children false}]
    (is (= expected-empty-facets (search-and-return-v2-facets)))))

(deftest some-facets-missing-test
  (fu/make-coll 1 "PROV1"
                (fu/science-keywords sk3 sk2)
                (fu/processing-level-id "PL1"))
  (is (= fr/partial-v2-facets (search-and-return-v2-facets))))
