(ns clj-wyag.util
  (:gen-class)
  (:require [zlib-tiny.core :as z]))

;;; other

(defn str-partition [n s]
  (map #(clojure.string/join %)
       (partition n s)))

;;; original => https://gist.github.com/hozumi/1472865
(defn sha1-str [s]
  (->> (-> "sha1"
           java.security.MessageDigest/getInstance
           (.digest (.getBytes s)))
       (map #(.substring
              (Integer/toString
               (+ (bit-and % 0xff) 0x100) 16) 1))
       (apply str)))

;;; bytes

(defn compress [s]
  (clojure.string/join
   (map #(format "%02x" %)
        (-> s
            z/str->bytes
            z/gzip))))

(defn decompress [s]
  (-> (byte-array
       (map #(unchecked-byte (Integer/parseInt % 16))
            (str-partition 2 s)))
      z/gunzip
      z/bytes->str))

;;; filepath

(defn str->real-path [str]
  (.toString (.toRealPath (.toPath (clojure.java.io/file str))
                          (into-array java.nio.file.LinkOption
                                      [java.nio.file.LinkOption/NOFOLLOW_LINKS]))))

;;; conf util

(defn write-conf [conf]
  (reduce #(if (= (type {}) (type (get conf %2)))
             (str %1 "[" (name %2) "]\n" (write-conf (get conf %2)))
             (str %1 (name %2) " = " (get conf %2) "\n"))
          ""
          (keys conf)))

(defn read-conf [path]
  (let [lines (map trim (split (slurp path) #"\n"))
        nspace (atom "")
        result (atom {})]
    (doseq [line lines]
      (cond (and (= \[ (first line)) (= \] (first (reverse line))))
            (reset! nspace (join (reverse (drop 1 (reverse (drop 1 line))))))
            (some #(= \= %) line)
            (let [parse (split line #"=")]
              (reset! result (merge-with merge @result
                                         (if (= @nspace "")
                                           {(keyword (trim (first parse)))
                                            (trim (join "=" (drop 1 parse)))}
                                           {(keyword @nspace)
                                            {(keyword (trim (first parse)))
                                             (trim (join "=" (drop 1 parse)))}}))))))
    @result))
