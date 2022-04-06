(ns fjson
  (:require [tech.v3.datatype.char-input :as char-input]
            [clojure.data.json :as clj-json]
            [jsonista.core :as jsonista]
            [criterium.core :as crit]
            [clojure.edn :as edn]
            [tech.viz.pyplot :as pyplot]
            [clojure.java.io :as io]))

(def testfiles (->> (-> (java.io.File. "data/")
                        (.list))
                    (filter #(.endsWith (str %) ".json"))
                    (mapv (fn [fname]
                            [fname (slurp (str "data/" fname))]))))

(def parse-fns
  {:clj-json #(clj-json/read-str %)
   :jsonista #(jsonista/read-value %)
   :dtype (char-input/parse-json-fn {:profile :immutable})
   :dtype-mutable (char-input/parse-json-fn {:profile :mutable})
   :dtype-mixed (char-input/parse-json-fn {:profile :mixed})
   :dtype-raw (char-input/parse-json-fn {:profile :raw})})


(defmacro benchmark-ms
  [op]
  `(let [bdata# (crit/quick-benchmark ~op nil)]
     {:mean (* (double (first (:mean bdata#))) 1e3)
      :variance (* (double (first (:variance bdata#))) 1e3)}))


(defn benchmark-data
  []
  (->> testfiles
       (map (fn [[fname fdata]]
              {:name fname
               :length (count fdata)
               :results (->> parse-fns
                             (map (fn [[fn-name parse-fn]]
                                    (assoc (benchmark-ms (parse-fn fdata))
                                           :engine fn-name)))
                             (sort-by :mean))}))
       (sort-by :length)))


(defn benchmark->file
  [fname]
  (spit fname (pr-str (benchmark-data))))

(comment
  (benchmark->file "jdk-8.edn")
  )


(defn flatten-results
  [fnames]
  (->> fnames
       (mapcat (fn [fname]
                 (let [jdk-name (.substring (str fname) 0 (- (count fname) 4))
                       raw-data (with-open [is (io/reader fname)]
                                  (edn/read (java.io.PushbackReader. is)))]
                   (->> raw-data
                        (mapcat (fn [file-results]
                                  (->> (:results file-results)
                                       (map #(assoc % :jdk jdk-name
                                                    :name (:name file-results)
                                                    :length (:length file-results))))))))))))

(defn chart-results
  ([& [fnames]]
   (-> {:$schema "https://vega.github.io/schema/vega-lite/v5.1.0.json"
        :mark {:type :point}
        :width 800
        :height 600
        :data {:values (vec (flatten-results (or fnames ["jdk-8.edn" "jdk-17.edn"])))}
        :encoding
        {:y {:field :mean, :type :quantitative :axis {:grid false}}
         :x {:field :length :type :quantitative}
         :color {:field :engine :type :nominal}
         :shape {:field :jdk :type :nominal}}}
       (pyplot/show))))


(comment
  (chart-results)
  )


(comment
  (def bigtext (slurp "data/json100k.json"))
  (def job (with-open [jfn (char-input/read-json-fn bigtext)]
             (jfn)))
  (require '[tech.v3.dataset :as ds])
  (-> (ds/->dataset "../../tech.all/tech.ml.dataset/test/data/stocks.csv")
      (ds/column-map "date" str ["date"])
      (ds/write! "data/stocks.json"))

  (benchmark-ms (char-input/read-json bigtext))
  ;; 560us
  (benchmark-ms (char-input/read-json bigtext :profile :mutable))
  ;; 345us
  (benchmark-ms (char-input/read-json bigtext :profile :raw))
  ;; 272us


  (dotimes [idx 10000]
    (let [jfn (char-input/read-json-fn bigtext :profile :mutable)]
      (jfn)))
  )