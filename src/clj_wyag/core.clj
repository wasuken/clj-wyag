(ns clj-wyag.core
  (:require [clj-sub-command.core :refer [parse-cmds]])
  (:gen-class))

(def options
  [["-h" "--help"]])

(def commands
  [["add" ""]
   ["cat-file" ""]
   ["checkout" ""]
   ["commit" ""]
   ["hash-object" ""]
   ["init" ""]
   ["log" ""]
   ["ls-tree" ""]
   ["merge" ""]
   ["rebase" ""]
   ["rev-parse" ""]
   ["rm" ""]
   ["show-ref" ""]
   ["tag" ""]])

(defn -main [& args]
  (let [parsed (parse-cmds args options commands)
        cmd (:command parsed)
        ags (:arguments parsed)
        current-path (str (-> (java.io.File. "") .getAbsolutePath) "/")]
    ))
