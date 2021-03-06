(ns graphql-clj.validator.errors
  (:require [graphql-clj.spec :as spec]
            [clojure.spec :as s]
            [clojure.string :as str]
            [graphql-clj.error :as ge]
            [graphql-clj.box :as box]))

(defn- conj-error [new-errors existing-errors]
  (into (or existing-errors []) (map #(if (map? %) % (hash-map :error %)) new-errors)))

(defn extract-loc
  [{:keys [instaparse.gll/start-line instaparse.gll/start-column]}]
  {:line start-line :column start-column})

(defn update-errors [ast & errors]
  (update ast :errors (partial conj-error errors)))

(defn render-naked-object [v]
  (str/replace (pr-str (into {} (map (fn [[k v]] [(str (name k) ":") v]) v))) #"\"" ""))

(defn render [v]
  (cond (string? v) (str "\"" v "\"")
        (map? v)    (render-naked-object v)
        :else       v))

(defn unboxed-render [v] (-> v box/box->val render))

(defn- missing-contains [spec containing-spec]
  (let [base-spec (s/get-spec (keyword (str (namespace containing-spec) "." (name containing-spec)) (name spec)))]
    (format "The NotNull field '%s' of type '%s' is missing" (name spec) (spec/remove-required (name base-spec)))))

(def default-type-preds (set (vals spec/default-specs)))

(defn- explain-problem [spec {:keys [pred via] :as problem}]
  (cond
    (= 'map? pred)
    (format "Expected '%s', found not an object" (name (s/get-spec spec)))

    (and (seq? pred) (= 'contains? (first pred)))
    (missing-contains (last pred) (s/get-spec (first via)))

    (default-type-preds pred)
    (format "%s value expected" (name (s/get-spec spec)))

    :else (ge/throw-error "Unhandled spec problem" {:spec spec :problem problem :base-spec (s/get-spec spec)})))

(defn explain-invalid [spec value]
  (->> (s/explain-data spec value)
       :clojure.spec/problems
       (map (partial explain-problem spec))
       (str/join ",")))

(defn duplicates
  "Return the duplicate values from a sequence"
  [s]
  (->> (frequencies s) (filter (fn [[_ v]] (> v 1))) keys))

(defn- duplicate-name-error [label vals duplicate-name]
  {:error (format "There can be only one %s named '%s'."
                  label
                  (if (= "variable" label) (str "$" duplicate-name) duplicate-name))
   :loc (extract-loc (meta (last vals)))})

(defn duplicate-name-errors [label map-fn vals]
  (->> (map map-fn vals)
       duplicates
       (map (partial duplicate-name-error label vals))))

(defn guard-duplicate-names [label map-fn vals s]
  (let [errors (duplicate-name-errors label map-fn vals)]
    (when-not (empty? errors)
      {:state (apply update-errors s errors)
       :break true})))

(defn guard-errors! [{:keys [errors]}]
  (when errors (throw (ex-info "Validation Error" {:errors errors}))))
