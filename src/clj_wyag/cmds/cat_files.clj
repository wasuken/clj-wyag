(ns clj-wyag.cmds.cat-files
  (:require [clj-wyag.util :refer :all]))

(defn cat-file [repo obj fmt]
  (let [obj (object-read repo (object-find repo obj fmt))]
    (prn (serialize obj))))

(defn cmd-cat-file [args]
  (let [repo (repo-find)]
    ;; ここはライブラリ作るなりして対処する。
    (cat-file repo (:object args) (:fmt args))))
