(ns clj-wyag.classes
  (:require [clj-wyag.util :refer :all]))

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
          (throw (Exception (format "%s is not Empty!" path)))))
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

(defn object-hash
  ([fd fmt] (object-hash fd fmt nil))
  ([fd fmt repo])
  (let [obj (case fmt
              "commit" (create-git-commit repo (slurp fd))
              "tree" (create-git-tree repo (slurp fd))
              "tag" (create-git-tag repo (slurp fd))
              "blob" (create-git-blob repo (slurp fd))
              (throw (Exception (format "Unknown type %s" fmt))))]
    (object-write obj repo)))

(defn kvlm-parse
  ([raw] (klvm-parse raw 0))
  ([raw start] (klvm-parse raw 0 nil))
  ([raw start dct]
   (let [dict (if (nil? dct) (sorted-map) dct)]
     (if (or (clojure.string/index-of raw \  start)
             (clojure.string/index-of raw \newline start))
       (merge dict {\  (subs raw (+ start 1))})
       (let [key (subs raw start (clojure.string/index-of raw \  start))
             end (reduce (fn [e x] (if-not (= (nth raw (clojure.string/index-of raw \newline (+ e 1)))
                                              \ )
                                     (reduced e)))
                         start
                         (cycle [1]))
             value (clojure.string/replace (subs end (+ spc 1)) #"\n ")]
         (kvlm-parse raw (+ end 1) (if (get dict key)
                                     (merge-with into dict {key [ (get dict key) value]})
                                     (merge-with (fn [new old] old) dict {key value}))))))))

(defn kvlm-serialize [kvlm]
  (let [conv-kvlm (reduce-kv (fn [m k v] (merge m {k (if (list? v)
                                                       v
                                                       [v])}))
                             (sorted-map)
                             kvlm)
        result (reduce (fn [ret k] (if-not (= (str k) "")
                                     (str ret
                                          (reduce (fn [r v]
                                                    (str k " " (clojure.string/replace v #"\n" "\n ") "\n"))
                                                  ""
                                                  (get conv-kvlm k)))
                                     ret)
                         ""
                         (keys kvlm)))] ;; valueを全てseqへ変換
    (str result "\n" (get kvlm ""))))

(defrecord GitCommit [fmt kvlm]
  GitObjectBase
  (serialize [this]
    (kvlm-serialize (:kvlm this)))
  (deserialize [this data]
    (->GitCommit (:fmt this) (kvlm-parse data))))

(defrecord GitTreeLeaf [mode path sha])

(defn create-git-tree-leaf [mode path sha]
  (->GitTreeLeaf mode path sha))

(defn tree-parse-one
  ([raw] (tree-parse-one raw 0))
  ([raw start]
   (let [xsp (clojure.string/split raw " ")
         mode (subs (first xsp) start)
         ysp (clojure.string/split (clojure.string/join " " (drop 1 xsp)) "\x00")
         path (subs (first ysp) 1)]

     [(+ (count (first ysp)) 21)
      (create-git-tree-leaf mode path (subs (str->hexstr (subs 1 22 (clojure.string/join "\x00" (drop 1 ysp)))) 2))])))

(defn tree-parse
  [raw]
  (let [pos (atom 0)
        max (count raw)
        lt (atom [])]
    (loop []
      (while (< @pos max)
        (let [result (tree-parse-one raw pos)]
          (reset! lt (conj @lt (nth result 1)))
          (reset! pos (nth result 0)))))))

(defrecord GitTree [fmt items]
  GitObjectBase
  (serialize [this]
    (tree-serialize this))
  (deserialize [this data]
    (->GitTree (:fmt this) (tree-parse data))))

(defn tree-checkout [repo tree path]
  (doseq [item items]
    (let [obj (object-read repo (:sha item))
          dest (repo-path path (:path item))]
      (cond (= "fmt" (:fmt obj))
            (do
              (.mkdirs dest)
              (tree-checkout repo obj dest))
            (= "blob" (:fmt obj))
            (spit dest (:blobdata obj))))))
