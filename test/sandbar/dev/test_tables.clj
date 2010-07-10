;; Copyright (c) Brenton Ashworth. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns sandbar.dev.test-tables
  (:use (clojure test)
        (sandbar core stateful-session
                 [test :only (t)])
        (sandbar.dev tables)))

(deftest test-merge-table-state-vecs
  (t "merge table sort state"
     (t "adding a new sort to empty list"
        (is (= (merge-table-state-vecs [] [:b :asc])
               [:b :asc])))
     (t "adding a new sort"
        (is (= (merge-table-state-vecs [:a :asc] [:b :asc])
               [:a :asc :b :asc])))
     (t "adding a new sort maintaining the correc order"
        (is (= (merge-table-state-vecs [:b :asc] [:a :asc])
               [:b :asc :a :asc])))
     (t "updating the sort direction"
        (is (= (merge-table-state-vecs [:b :asc :a :desc] [:b :desc])
               [:b :desc :a :desc])))
     (t "removing the last sort"
        (is (= (merge-table-state-vecs [:b :asc :a :desc] [:a :remove])
               [:b :asc])))
     (t "removing the first sort"
        (is (= (merge-table-state-vecs [:b :asc :a :desc] [:b :remove])
               [:a :desc]))))
  (t "merge table filter state"
     (t "adding a new filter to empty list"
        (is (= (merge-table-state-vecs [] [:b "a"])
               [:b "a"])))
     (t "adding a new filter"
        (is (= (merge-table-state-vecs [:a "a"] [:b "b"])
               [:a "a" :b "b"])))
     (t "adding a new filter maintaining the correc order"
        (is (= (merge-table-state-vecs [:b "b"] [:a "a"])
               [:b "b" :a "a"])))
     (t "updating a filter"
        (is (= (merge-table-state-vecs [:b "b" :a "a"] [:b "c"])
               [:b "c" :a "a"])))
     (t "removing the last filter"
        (is (= (merge-table-state-vecs [:b "b" :a "a"] [:a :remove])
               [:b "b"])))
     (t "removing the first filter"
        (is (= (merge-table-state-vecs [:b "b" :a "a"] [:b :remove])
               [:a "a"])))))

(defn test-table-state [s]
  {:table-state {:test-table s}})

(deftest test-update-table-state!
  (binding [*table-id* :test-table]
    (t "update table state sort state"
      (t "when adding sort to initially empty state"
         (binding [*sandbar-session* (atom {})]
           (is (= (update-table-state! {"sort-asc" "a"})
                  {:sort [:a :asc] :filter []}))))
      (t "when changing the direction of an existing sort"
         (binding [*sandbar-session*
                   (atom (test-table-state {:sort [:a :asc]}))]
           (is (= (update-table-state! {"sort-desc" "a"})
                  {:sort [:a :desc] :filter []}))))
      (t "when adding multiple sorts at the same time"
         (binding [*sandbar-session*
                   (atom (test-table-state {:sort [:a :asc]}))]
           (is (= (update-table-state!
                   {"sort-asc" "b" "sort-desc" "c"})
                  {:sort [:a :asc :b :asc :c :desc] :filter []}))))
      (t "when adding a new sort to an existing sort"
         (binding [*sandbar-session*
                   (atom (test-table-state {:sort [:b :asc]}))]
           (is (= (update-table-state! {"sort-desc" "a"})
                  {:sort [:b :asc :a :desc] :filter []}))))
      (t "when removing an existing sort"
         (binding [*sandbar-session* (atom (test-table-state
                                            {:sort [:b :asc :a :asc]}))]
           (is (= (update-table-state! {"remove-sort" "a"})
                  {:sort [:b :asc] :filter []})))))
    (t "update table filter state"
       (t "when adding filter to initially empty state"
          (binding [*sandbar-session* (atom {})]
            (is (= (update-table-state!
                    {"filter" "a" "filter-value" "v-a"})
                   {:sort [] :filter [:a "v-a"]}))))
       (t "when changing the value of a filter"
          (binding [*sandbar-session*
                    (atom (test-table-state {:filter [:a "v-a"]}))]
            (is (= (update-table-state!
                    {"filter" "a" "filter-value" "v-b"})
                   {:sort [] :filter [:a "v-b"]}))))
       (t "when adding a new filter to an existing filter"
          (binding [*sandbar-session*
                    (atom (test-table-state {:filter [:b "v-b"]}))]
            (is (= (update-table-state!
                    {"filter" "a" "filter-value" "v-a"})
                   {:sort [] :filter [:b "v-b" :a "v-a"]}))))
       (t "when removing an existing filter"
          (binding [*sandbar-session* (atom (test-table-state
                                             {:filter [:b "v-b" :a "v-a"]}))]
            (is (= (update-table-state! {"remove-filter" "a"})
                   {:sort [] :filter [:b "v-b"]})))))))

(deftest test-build-page-and-sort-map
  (is (= (build-page-and-sort-map {:sort [:a :asc :b :desc]})
         {:sort [:asc "a" :desc "b"]})))

(deftest test-build-filter-map
  (is (= (build-filter-map {:filter [:a "v-a" :b "v-b"]})
         {:a "v-a" :b "v-b"})))

(deftest test-current-page-and-sort!
  (binding [*table-id* :test-table
            *sandbar-session* (atom (test-table-state {:sort [:b :asc]}))]
    (is (= (current-page-and-sort! {"sort-desc" "a"})
           {:sort [:asc "b" :desc "a"]}))))

(deftest test-create-table-sort-and-filter-controls
  (binding [*table-id* :test-table
            *sandbar-session* (atom (test-table-state {:sort [:a :asc]
                                                       :filter [:b "v-b"]}) )]
    (is (= (create-table-sort-and-filter-controls {})
           [:div {:class "filter-and-sort-controls"}
            [:div "Remove sort: "
             [:a {:href "javascript:removeSort_test_table('a');"} [:a]]]
            [:div "Remove filter: "
             [:a {:href "javascript:removeFilter_test_table('b');"}
              [":b = v-b"]]]]))))

(deftest test-table-cell
  (binding [*table-id* :a]
    (t "create table cell"
      (t "with just a value"
         (is (= (table-cell "v")
                [:td "v"])))
      (t "with a value and attributes"
         (is (= (table-cell {:attr {:class "c"} :value "v"})
                [:td {:class "c"} "v"])))
      (t "with a nil value"
         (is (= (table-cell nil)
                [:td])))
      (t "with a filter"
         (is (= (table-cell {:value "v"
                             :actions #{:filter} :column :x})
                [:td [:a {:href "javascript:addFilter_a('x', 'v');"} ["v"]]])))
      (t "with a filter and attributes"
         (is (= (table-cell {:value "v" :attr {:class "c"}
                             :actions #{:filter} :column :x})
                [:td {:class "c"}
                 [:a {:href "javascript:addFilter_a('x', 'v');"} ["v"]]])))
      (t "with multiple values"
         (is (= (table-cell "v1" "v2" "v3")
                [:td "v1" "v2" "v3"])))
      (t "with multiple values one of which is nil"
         (is (= (table-cell "v1" nil "v3")
                [:td "v1" "v3"]))))))

(def table-javascript
     "
function sortColumn_test_table(dir, column) {
  updateTable_test_table('/ideas?sort-' + dir + '=' + column);
}

function removeSort_test_table(column) {
  updateTable_test_table('/ideas?remove-sort=' + column);
}

function addFilter_test_table(column, value) {
  updateTable_test_table('/ideas?filter=' + column + '&filter-value=' + value);
}

function removeFilter_test_table(column) {
  updateTable_test_table('/ideas?remove-filter=' + column);
}

function updateTable_test_table(uri) {
  new Ajax.Request(uri, {
    onSuccess: function(response) {
      var data = response.responseJSON;
      displayResults_test_table(data);
    }
  });
}

function displayResults_test_table(data) {
  $('test-table').replace(data['html']);
}")

(deftest test-js
  (is (= (js "test-table" "/ideas" :prototype)
         table-javascript)))



