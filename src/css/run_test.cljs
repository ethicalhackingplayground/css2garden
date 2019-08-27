(ns css.run-test
  (:require [clojure.test :refer [deftest is]]
            [css :as js-css]
            [garden.core :as garden]
            [clojure.string :as str]
            [clojure.walk :refer [postwalk]]))

(defn remove-position
  [item]
  (if (map? item) (dissoc item :position :source) item))

(defn parse
  [input]
  (get-in (postwalk remove-position
                    (-> input
                        js-css/parse
                        (js->clj :keywordize-keys true)))
          [:stylesheet :rules]))

(deftest parse-test
  (is (= [{:type "rule",
           :selectors ["body"],
           :declarations
             [{:type "declaration", :property "font-size", :value "12px"}]}]
         (parse "body { font-size: 12px; }")))
  (is (= [{:type "rule",
           :selectors ["body h1"],
           :declarations
             [{:type "declaration", :property "font-size", :value "12px"}]}]
         (parse "body h1 { font-size: 12px; }")))
  (is (= [{:type "rule",
           :selectors ["body" "h1"],
           :declarations
             [{:type "declaration", :property "font-size", :value "12px"}]}]
         (parse "body, h1 { font-size: 12px; }")))
  (is (= [{:type "rule",
           :selectors ["body h1" "body h2"],
           :declarations
             [{:type "declaration", :property "font-size", :value "12px"}]}]
         (parse "body h1, body h2 { font-size: 12px; }"))))

(deftest garden-test
  (is (= "body {\n  font-size: 18px;\n}"
         (garden/css [:body {:font-size "18px"}])))
  (is (= "body h1 {\n  font-size: 18px;\n}"
         (garden/css [:body [:h1 {:font-size "18px"}]])))
  (is
    (= "body h1 {\n  font-size: 18px;\n}\n\nbody h2 {\n  font-size: 18px;\n}"
       (garden/css [:body [:h1 {:font-size "18px"}] [:h2 {:font-size "18px"}]])
       (garden/css [:body [:h1 {:font-size "18px"}] :body
                    [:h2 {:font-size "18px"}]])))
  (is (= "body, h1 {\n  font-size: 18px;\n}"
         (garden/css [:body :h1 {:font-size "18px"}]))))

(defn ->declarations
  [input]
  (reduce (fn [accum item]
            (assoc accum (keyword (:property item)) (:value item)))
    {}
    input))

(defn arrays [[head & tail] attrs] [head (if tail (arrays tail attrs) attrs)])

(deftest arrays-test
  (is (= [:x {:a 1}] (arrays [:x] {:a 1})))
  (is (= [:x [:y {:a 1}]] (arrays [:x :y] {:a 1}))))

(defn ->garden
  [input]
  (reduce
    (fn [accum {:keys [selectors declarations]}]
      (let [splits (map #(map keyword (str/split % #" ")) (map name selectors))]
        (if (> (count (first splits)) 1)
          (concat accum (arrays (first splits) (->declarations declarations)))
          (concat accum
                  (map keyword selectors)
                  [(->declarations declarations)]))))
    []
    input))

(deftest ->garden-test
  (is (= [:body {:font-size "12px"}]
         (->garden (parse "body { font-size: 12px; }"))))
  (is (= [:body {:font-size "12px", :font-weight "bold"}]
         (->garden (parse "body { font-size: 12px; font-weight: bold; }"))))
  (is (= [:body :h1 {:font-size "12px"}]
         (->garden (parse "body, h1 { font-size: 12px; }"))))
  (is (= [:body [:h1 {:font-size "12px"}]]
         (->garden (parse "body h1 { font-size: 12px; }"))))
  #_(is (= [:body [:h1 {:font-size "12px"}] :body [:h2 {:font-size "12px"}]]
           (->garden (parse "body h1, body h2 { font-size: 12px; }"))))
  (is
    (= [:body {:font-size "12px"} :h1 {:font-family "\"Geneva\""}]
       (->garden
         (parse "body { font-size: 12px } h1 { font-family: \"Geneva\"; }")))))