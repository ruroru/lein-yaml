(ns leiningen.yaml-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [leiningen.yaml :as ly]
            [leiningen.core.main :as main]))

(def ^:dynamic *tmp-dir* nil)

(defn- with-tmp-dir [f]
  (let [dir (io/file (System/getProperty "java.io.tmpdir")
                     (str "lein-yaml-test-" (System/nanoTime)))]
    (.mkdirs dir)
    (binding [*tmp-dir* dir]
      (try (f)
           (finally
             (doseq [file (reverse (file-seq dir))]
               (.delete file)))))))

(use-fixtures :each with-tmp-dir)

(deftest updates-existing-file
  (let [f (io/file *tmp-dir* "chart.yaml")
        file-path (.getPath f)
        mock-project {:version "1.0.0"
                      :yaml    {file-path [[[:version] [:appVersion]]]}}]

    (spit f "name: my-chart\nversion: 0.0.1\nappVersion: v1.0.0\n")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project))

    (let [result (yaml/parse-string (slurp f))]
      (is (= "my-chart" (:name result)) "Untouched keys must be preserved")
      (is (= "0.0.1" (:version result)) "Keys not targeted by a destination path stay unchanged")
      (is (= "1.0.0" (:appVersion result)) "Project's [:version] is written to the file's [:appVersion]"))))

(deftest creates-file-when-missing
  (let [f (io/file *tmp-dir* "new-chart.yaml")
        file-path (.getPath f)
        mock-project {:version "2.3.4"
                      :yaml    {file-path [[[:version] [:appVersion]]]}}]

    (is (not (.exists f)) "File should not exist before running")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project))

    (is (.exists f) "File should be created")
    (let [result (yaml/parse-string (slurp f))]
      (is (= "2.3.4" (:appVersion result))))))

(deftest creates-parent-directories
  (let [f (io/file *tmp-dir* "sub" "dir" "chart.yaml")
        file-path (.getPath f)
        mock-project {:version "1.0.0"
                      :yaml    {file-path [[[:version] [:version]]]}}]

    (is (not (.exists (.getParentFile f))))

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project))

    (is (.exists f))
    (let [result (yaml/parse-string (slurp f))]
      (is (= "1.0.0" (:version result))))))

(deftest duplicate-source-paths
  (let [f (io/file *tmp-dir* "chart.yaml")
        file-path (.getPath f)
        mock-project {:version "1.2.3"
                      :yaml    {file-path [[[:version] [:version]]
                                           [[:version] [:appVersion]]]}}]

    (spit f "name: my-chart\nversion: 0.0.1\nappVersion: 0.0.1\n")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project))

    (let [result (yaml/parse-string (slurp f))]
      (is (= "1.2.3" (:version result)) "First destination updated")
      (is (= "1.2.3" (:appVersion result)) "Second destination updated with same source"))))

(deftest multiple-mappings-in-one-file
  (let [f (io/file *tmp-dir* "chart.yaml")
        file-path (.getPath f)
        mock-project {:version     "3.0.0"
                      :description "My awesome app"
                      :yaml        {file-path [[[:version]     [:appVersion]]
                                               [[:description] [:description]]]}}]

    (spit f "name: my-chart\nappVersion: old\n")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project))

    (let [result (yaml/parse-string (slurp f))]
      (is (= "my-chart" (:name result)))
      (is (= "3.0.0" (:appVersion result)))
      (is (= "My awesome app" (:description result))))))

(deftest nested-destination-path
  (let [f (io/file *tmp-dir* "values.yaml")
        file-path (.getPath f)
        mock-project {:version "5.0.0"
                      :yaml    {file-path [[[:version] [:image :tag]]]}}]

    (spit f "image:\n  repository: myrepo\n  tag: old\n")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project))

    (let [result (yaml/parse-string (slurp f))]
      (is (= "myrepo" (get-in result [:image :repository])) "Sibling keys preserved")
      (is (= "5.0.0" (get-in result [:image :tag]))))))

(deftest nested-source-path
  (let [f (io/file *tmp-dir* "chart.yaml")
        file-path (.getPath f)
        mock-project {:metadata {:build-id "abc123"}
                      :yaml     {file-path [[[:metadata :build-id] [:annotations :buildId]]]}}]

    (spit f "name: my-chart\n")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project))

    (let [result (yaml/parse-string (slurp f))]
      (is (= "my-chart" (:name result)))
      (is (= "abc123" (get-in result [:annotations :buildId]))))))

(deftest multiple-files
  (let [f1 (io/file *tmp-dir* "chart1.yaml")
        f2 (io/file *tmp-dir* "chart2.yaml")
        path1 (.getPath f1)
        path2 (.getPath f2)
        mock-project {:version "1.0.0"
                      :yaml    {path1 [[[:version] [:appVersion]]]
                                path2 [[[:version] [:version]]]}}]

    (spit f1 "name: chart1\n")
    (spit f2 "name: chart2\n")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project))

    (let [r1 (yaml/parse-string (slurp f1))
          r2 (yaml/parse-string (slurp f2))]
      (is (= "1.0.0" (:appVersion r1)))
      (is (= "1.0.0" (:version r2))))))

(deftest selective-path-argument
  (let [f1 (io/file *tmp-dir* "chart1.yaml")
        f2 (io/file *tmp-dir* "chart2.yaml")
        path1 (.getPath f1)
        path2 (.getPath f2)
        mock-project {:version "1.0.0"
                      :yaml    {path1 [[[:version] [:appVersion]]]
                                path2 [[[:version] [:version]]]}}]

    (spit f1 "name: chart1\n")
    (spit f2 "name: chart2\n")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project path1))

    (let [r1 (yaml/parse-string (slurp f1))
          r2 (yaml/parse-string (slurp f2))]
      (is (= "1.0.0" (:appVersion r1)) "Selected file should be updated")
      (is (nil? (:version r2)) "Unselected file should not be touched"))))

(deftest warns-on-empty-config
  (let [warnings (atom [])
        mock-project {:version "1.0.0"
                      :yaml    {}}]

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& args] (swap! warnings conj (apply str args)))]
      (ly/yaml mock-project))

    (is (pos? (count @warnings)) "Should warn when :yaml config is empty")))

(deftest warns-on-unknown-path-argument
  (let [warnings (atom [])
        f (io/file *tmp-dir* "chart.yaml")
        file-path (.getPath f)
        mock-project {:version "1.0.0"
                      :yaml    {file-path [[[:version] [:appVersion]]]}}]

    (spit f "name: my-chart\n")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& args] (swap! warnings conj (apply str args)))]
      (ly/yaml mock-project "nonexistent.yaml"))

    (is (some #(.contains % "nonexistent.yaml") @warnings)
        "Should warn about unknown path")))

(deftest handles-blank-existing-file
  (let [f (io/file *tmp-dir* "empty.yaml")
        file-path (.getPath f)
        mock-project {:version "1.0.0"
                      :yaml    {file-path [[[:version] [:appVersion]]]}}]

    (spit f "   \n  \n")

    (with-redefs [main/info (fn [& _])
                  main/warn (fn [& _])]
      (ly/yaml mock-project))

    (let [result (yaml/parse-string (slurp f))]
      (is (= "1.0.0" (:appVersion result))))))