(ns clj-wyag.cmds.checkout
  (:require [clj-wyag.classes :refer :all]
            [clj-wyag.util :refer :all]))

(defn cmd-checkout [args]
  (let [repo (repo-find)
        obj (atom (object-read repo (object-find repo (:commit args))))]
    (when (= (:fmt obj) "commit")
      (reset! obj (object-read repo (get (:kvlm obj) "tree"))))
    (let [f (clojure.java.io/file (:path args))]
      (if (.exists f)
        (when-not (.isDirectory f)
          (throw (Exception (format "%s is not a directory!" (:path args)))))
        (when-not (-> wtf .list empty?)
          (throw (Exception (format "%s is not empty!"  (:path args)))))))))
