(ns graphql-clj.validator.rules.no-undefined-variables
  "A GraphQL operation is only valid if all variables encountered, both directly
   and via fragment spreads, are defined by that operation."
  (:require [graphql-clj.visitor :refer [defnodevisitor]]
            [graphql-clj.validator.errors :as ve]
            [clojure.spec :as s]
            [graphql-clj.spec :as spec]))

(defn- undefined-variable-error [{:keys [variable-name]}]
  {:error (format "Variable '$%s' is not defined." variable-name)
   :loc   (ve/extract-loc (meta variable-name))})

(defnodevisitor undefined-variable :pre :argument
  [{:keys [variable-name] :as n} s]
  (when (and variable-name (not (s/get-spec (spec/spec-for-var-usage variable-name s))))
    {:state (ve/update-errors s (undefined-variable-error n))
     :break true}))

(def rules [undefined-variable])
