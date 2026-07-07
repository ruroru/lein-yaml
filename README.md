# lein-yaml

A [Leiningen](https://leiningen.org) plugin that updates YAML files (for
example a Helm `chart.yaml`) with values declared directly in your
`project.clj`.

## Installation

Add the plugin to `:plugins` in your `project.clj` (or your user
`~/.lein/profiles.clj`):

```clojure
:plugins [[lein-yaml "0.1.0-SNAPSHOT"]]
```

## Configuration

Add a `:yaml` map to `project.clj`. Each key is the path to a YAML file, and
its value is a map of values to merge into that file:

```clojure
:yaml {"chart.yaml"
       #{:version
         }}
```

You can configure multiple files at once:

```clojure
:yaml {"chart.yaml"      {:version "1.2.3"}
       "config/app.yaml" {:log {:level "info"}}}
```

## Usage

Update every configured file:

```bash
lein yaml
```

Update only specific files:

```bash
lein yaml chart.yaml
```

## Behaviour

- **Deep merge.** Nested maps are merged into the existing document, so keys
  that you do not mention are left untouched. Scalar and sequence values are
  replaced.
- **Missing files are created**, including any missing parent directories.
- Output is written as block-style YAML.

## Development

Run the tests with:

```bash
lein test
```

## License

Copyright © 2026

Distributed under the Eclipse Public License, the same as Clojure.
