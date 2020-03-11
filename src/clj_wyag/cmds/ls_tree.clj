(ns clj-wyag.cmds.ls-tree
  (:require [clj-wyag.classes :refer :all]
            [clj-wyag.util :refer :all]))

(defn cmd-ls-tree [args]
  (let [repo (repo-find)
        obj (obj-read repo (object-find (:object args) "tree"))]
    (doseq [item (:items obj)]
      (prn (format "%s %s %s\t%s"
                   (clojure.string/join (repeat (- 6 (count (:mode item))) "0"))
                   (object-read repo (:sha item))
                   (:sha item)
                   (:path item))))))
