(ns translator
  (:require [rewrite-clj.zip :as z]
            [rewrite-clj.node :as n]
            [clojure.string :as str]))

(defn- electric-name->view-name
  "Convert Electric function name to Re-frame view name"
  [electric-name]
  (let [name-str (name electric-name)
        ;; Convert CamelCase to kebab-case
        kebab-case (-> name-str
                       (str/replace #"([a-z])([A-Z])" "$1-$2")
                       (str/lower-case))]
    (symbol (str kebab-case "-view"))))

(defn- dom-element?
  "Check if a node represents a DOM element call"
  [zloc]
  (when (z/list? zloc)
    (when-let [first-child (z/down zloc)]
      (and (z/sexpr-able? first-child)
           (symbol? (z/sexpr first-child))
           (str/starts-with? (str (z/sexpr first-child)) "dom/")))))

(defn- extract-tag-name
  "Extract HTML tag name from dom/tag symbol"
  [dom-symbol]
  (-> (str dom-symbol)
      (str/split #"/")
      second
      keyword))

(defn- translate-dom-text
  "Extract text from (dom/text \"...\") node"
  [zloc]
  (when (z/list? zloc)
    (when-let [first-child (z/down zloc)]
      (when (= 'dom/text (z/sexpr first-child))
        (when-let [text-node (z/right first-child)]
          (z/sexpr text-node))))))

(defn- translate-dom-props
  "Extract props from (dom/props {...}) node"
  [zloc]
  (when (z/list? zloc)
    (when-let [first-child (z/down zloc)]
      (when (= 'dom/props (z/sexpr first-child))
        (when-let [props-node (z/right first-child)]
          (z/sexpr props-node))))))

(defn- translate-dom-element
  "Translate a single Electric DOM element to Hiccup"
  [zloc]
  (cond
    ;; Handle (dom/text "...")
    (translate-dom-text zloc)
    (translate-dom-text zloc)
    
    ;; Handle (dom/props {...})
    (translate-dom-props zloc)
    (translate-dom-props zloc)
    
    ;; Handle DOM elements
    (dom-element? zloc)
    (let [tag-symbol (z/sexpr (z/down zloc))
          tag-name (extract-tag-name tag-symbol)
          children (loop [child (z/right (z/down zloc))
                          result []]
                     (if child
                       (recur (z/right child) 
                              (conj result (translate-dom-element child)))
                       result))
          ;; Filter out nils
          children (remove nil? children)]
      
      (cond
        ;; No children
        (empty? children)
        [tag-name]
        
        ;; First child is a map (props)
        (map? (first children))
        (into [tag-name (first children)] (rest children))
        
        ;; Regular children
        :else
        (into [tag-name] children)))
    
    ;; For other nodes, try to get sexpr
    (z/sexpr-able? zloc)
    (z/sexpr zloc)
    
    :else
    nil))

(defn- find-client-binding-body
  "Find the body expressions inside (e/client (binding [...] body...))"
  [zloc]
  (when-let [client-node (z/find zloc z/next 
                                 #(and (z/list? %)
                                       (= 'e/client (z/sexpr (z/down %)))))]
    (when-let [binding-node (z/find (z/down client-node) z/right
                                    #(and (z/list? %)
                                          (= 'binding (z/sexpr (z/down %)))))]
      ;; Navigate to the binding vector, then collect all siblings after it
      (when-let [binding-vec (z/right (z/down binding-node))]
        (loop [node (z/right binding-vec)
               bodies []]
          (if node
            (recur (z/right node) (conj bodies node))
            bodies))))))

(defn- create-view-function
  "Create a Re-frame view function from Electric body expressions"
  [electric-name body-zlocs]
  (let [view-name (electric-name->view-name electric-name)
        translated-bodies (map translate-dom-element body-zlocs)]
    
    (if (= 1 (count translated-bodies))
      ;; Single element - no React Fragment needed
      (list 'defn view-name []
            (first translated-bodies))
      ;; Multiple elements - wrap in React Fragment
      (list 'defn view-name []
            (into [:<>] translated-bodies)))))

(defn translate
  "Translate Electric code to Re-frame components.
   Returns a map with :views, :events, and :subs keys."
  [electric-code]
  ;; Convert the code to a string for rewrite-clj
  (let [code-str (pr-str electric-code)
        zloc (z/of-string code-str)]
    
    ;; Find the e/defn form
    (if-let [defn-zloc (z/find-value zloc z/next 'e/defn)]
      (let [;; Get the function name (move right from e/defn)
            name-zloc (z/right defn-zloc)
            electric-name (z/sexpr name-zloc)
            ;; Find body expressions
            body-zlocs (find-client-binding-body (z/up defn-zloc))]
        
        (if (seq body-zlocs)
          {:views [(create-view-function electric-name body-zlocs)]
           :events []
           :subs []}
          ;; Empty body
          {:views [(create-view-function electric-name [])]
           :events []
           :subs []}))
      ;; No e/defn found
      {:views []
       :events []
       :subs []})))

;; Alternative version that works directly with files
(defn translate-file
  "Translate an Electric file to Re-frame components.
   Returns a map with :views, :events, and :subs keys."
  [file-path]
  (let [zloc (z/of-file file-path)
        ;; Find all e/defn forms in the file
        views (loop [loc zloc
                     result []]
                (if-let [defn-loc (z/find-tag loc z/next :list)]
                  (if (= 'e/defn (z/sexpr (z/down defn-loc)))
                    (let [name-loc (z/right (z/down defn-loc))
                          electric-name (z/sexpr name-loc)
                          body-locs (find-client-binding-body defn-loc)
                          view-fn (create-view-function electric-name (or body-locs []))]
                      (recur (z/right defn-loc) (conj result view-fn)))
                    (recur (z/next defn-loc) result))
                  result))]
    {:views views
     :events []
     :subs []}))
