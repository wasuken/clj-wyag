(ns clj-wyag.cmds.log
  (:require [clj-wyag.classes :refer :all]
            [clj-wyag.util :refer :all]))

(defn cmd-log [args]
  (let [repo (repo-find)]
    (prn "digraph wyaglog{")
    (log-graphviz repo (object-find repo (:commit args)) (set))
    (prn "}")))

(defn log-graphviz [repo sha seen]
  (let [commit (object-read repo sha)]
    (if-not (or (nil? (get seen sha))
                (not (nil? (some #(= % "parent") (keys (:kvlm commit))))))
      (let [seen2 (set (union seen [sha]))
            parents (if (list? (get (:kvlm commit) "parent"))
                      (get (:kvlm commit) "parent")
                      [(get (:kvlm commit) "parent")])]
        (doseq [p parents]
          ;; ここ、Pythonではdecodeしてるけどこっちでもやる必要あると思うんだが。
          (format "c_%s -> c_%s:" sha p)
          (log-graphviz repo p seen2))))))
