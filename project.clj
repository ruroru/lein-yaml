(defproject org.clojars.jj/lein-yaml "1.0.0-SNAPSHOT"
  :description "A Leiningen plugin that updates YAML files (e.g. a Helm chart.yaml) with values declared in project.clj."
  :url "https://github.com/your-org/lein-yaml"
  :license {:name "Eclipse Public License"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[clj-commons/clj-yaml "1.0.27"]
                 [leiningen-core "2.13.0"]]

  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]

  :strict-check {:filter ["leiningen.core"]}

  :plugins [[org.clojars.jj/bump "1.0.4"]
            [org.clojars.jj/strict-check "1.1.0"]
            [org.clojars.jj/lein-git-tag "1.0.1"]
            [org.clojars.jj/bump-md "1.1.0"]]
  )
