(ns clj-wyag.classes
  (:require [clj-wyag.util :refer :all]
            [zlib-tiny.core :as z])
  (:import (java.nio Path))
  (:gen-class))

(defrecord Repository [worktree gitdir conf])

(defn create-repository
  ([path] (create-repository path false))
  ([path force]
   (when-not (.isDirectory (clojure.java.io/file (str path ".git")))
     (throw (Exception (format "Not a Git repository %s" path))))
   (->Repository path (repo-path path ".git") (read-conf path ".git" "config"))))

(defmacro repo-path [repo & path-lst]
  `(clojure.java.io/file ~repo ~@path-lst))

(defn repo-dir
  ([repo path-lst] (repo-dir repo path-lst false))
  ([repo path-lst mkdir]
   (let [path (eval `(repo-path ~repo ~@path-lst))
         return (atom nil)]
     (cond (and (.exists path) (not (.isDirectory path)))
           (throw (Exception (format "Not a directory %s" path)))
           (.isDirectory path)
           (reset! return path))
     (if (and (nil? @return) mkdir)
       (do
         (.mkdirs path)
         (reset! return path)))
     @result)))

(defn repo-file
  ([repo path-lst] (repo-file repo path-lst false))
  ([repo path-lst mkdir]
   (if (repo-dir repo path-lst mkdir)
     (eval `(repo-path ~repo ~@path-lst)))))

(defn repo-default-config []
  {:core {:repositoryformatversion "0"
          :filemode "false"
          :bare "false"}})

(defn repo-create [path]
  (let [repo (create-repository path true)
        wtf (clojure.java.io/file (.worktree repo))]
    (if (.exists wtf)
      (do
        (when-not (.isDirectory wtf)
          (throw (Exception (format "%s is not a directory!" path))))
        (when-not (-> wtf .list empty?)
          (throw (Exception (format "%s is not a directory!" path)))))
      (.mkdirs wtf))
    (repo-dir repo ["branches"] true)
    (repo-dir repo ["objects"] true)
    (repo-dir repo ["refs" "tags"] true)
    (repo-dir repo ["refs" "heads"] true)
    (spit (.getPath (repo-file repo "description"))
          "Unnamed repository; edit this file 'description' to name the repository.\n")
    (spit (.getPath (repo-file repo "HEAD"))
          "ref: refs/heads/master\n")
    (spit (.getPath (repo-file repo "config")) (write-conf (repo-default-conf)))
    repo))

(defn repo-find
  ([] (repo-find ""))
  ([path] (repo-find path true))
  ([path required]
   (let [rpath (str->real-path path)
         parent (str->real-path "..")]
     (cond (.isDirectory (clojure.java.io/file rpath ".git"))
           (create-repository rpath)
           (= parent rpath)
           (if required
             (throw (Exception "No git directory.")))
           :else
           (repo-find parent required)))))

(defn object-read [repo sha]
  (let [path (repo-file repo (str (take 2 sha)) (str (drop 2 sha)))
        raw (decompress (slurp path))
        raw-sp (clojure.string/split raw #" ")
        fmt (first raw-sp)
        raw-zero-sp (clojure.string/split (clojure.string/join " " (drop 1 raw-sp)) #"\x00")
        size (Integer/parseInt (first raw-zero-sp))]
    (when-not (size (count (clojure.string/join "\x00" (drop 1 raw-zero-sp))))
      (throw (Exception (format "Malformed object %s: bad length" sha))))
    (case fmt
      "commit" (make-git-commit repo y)
      "tree" (make-git-tree repo y)
      "tag" (make-git-tag repo y)
      "blob" (make-git-blob repo y)
      (throw (Exception (format "Unknown type %s for object %s" fmt sha))))))

(defn object-find
  ([repo name] (object-find repo name nil))
  ([repo name fmt] (object-find repo name true))
  ([repo name fmt follow]) name)

(defn object-write
  ([obj] (object-write obj true))
  ([obj actually-write]
   (let [data (serialize obj)
         result (str (:fmt obj) " " (str (count data)) "\x00" data)
         sha (sha1-str result)]
     (if actually-write
       (let [path (repo-file (:repo obj) "objects" (subs sha 0 2) actually-write)]
         (spit path (compress result)))))))

(defprotocol GitObjectBase
  (serialize [this])
  (deserialize [this data]))

(defrecord GitObject [repo]
  GitObjectBase
  (serialize [this]
    (throw (Exception "Unimplemented!")))
  (deserialize [this data]
    (throw (Exception "Unimplemented!"))))

(defrecord GitBlob [fmt blob-data]
  GitObjectBase
  (serialize [this] blobdata)
  (deserialize [this data]) (->GitBlob (:fmt this) data))
