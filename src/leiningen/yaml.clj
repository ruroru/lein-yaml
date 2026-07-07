(ns leiningen.yaml
  "Leiningen task that updates YAML files with values read from project.clj.

  Configuration lives under the top-level `:yaml` key in project.clj and maps a
  file path to a sequence of `[source-path destination-path]` pairs. This sequential
  format allows reading from the same source path multiple times:

      :yaml {\"chart.yaml\" [[[:version] [:version]]
                            [[:version] [:appVersion]]]}

  Running `lein yaml` writes every configured file."
  (:require [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [leiningen.core.main :as main]))

(defn- read-yaml
  "Parse an existing YAML file, or return an empty map when it does not exist."
  [^java.io.File file]
  (if (.exists file)
    (let [content (slurp file)]
      (if (str/blank? content)
        {}
        (yaml/parse-string content)))
    {}))

(defn- write-yaml
  "Serialize `data` to `file` as block-style YAML."
  [file data]
  (spit file (yaml/generate-string data :dumper-options {:flow-style :block})))

(defn- apply-mappings
  "Given the `project` map, the existing YAML `doc`, and a sequence of
  `[source-path destination-path]` pairs, read each source path from the project and
  assoc its value into the destination path of the document."
  [project doc mappings]
  (reduce (fn [acc [src-path dest-path]]
            (assoc-in acc dest-path (get-in project src-path)))
          doc
          mappings))

(defn- update-file
  "Read each configured source path from `project` and write it to the matching
  destination path in the YAML document at `path`."
  [project path mappings]
  (let [^java.io.File file (io/file path)
        merged             (apply-mappings project (read-yaml file) mappings)]
    (when-let [^java.io.File parent (.getParentFile file)]
      (.mkdirs parent))
    (write-yaml file merged)))

(defn yaml
  [project & paths]
  (let [config (:yaml project)]
    (cond
      (empty? config)
      (main/warn "No :yaml configuration found in project.clj. Add a :yaml map, e.g.\n"
                 "  :yaml {\"chart.yaml\" [[[:version] [:version]]]}")

      :else
      (let [selected (if (seq paths)
                       (select-keys config paths)
                       config)]
        (when (seq paths)
          (doseq [p paths]
            (when-not (contains? config p)
              (main/warn "Warning:" p "is not present in the :yaml config; skipping."))))
        (if (empty? selected)
          (main/warn "Nothing to update.")
          (doseq [[path mappings] selected]
            (update-file project path mappings)
            (main/info "Updated" path)))))))