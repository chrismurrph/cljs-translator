(ns translator.translator
  (:require [rewrite-clj.zip :as z]
            [rewrite-clj.node :as n]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.walk :as walk]))

(defn- electric-name->view-name
  "Convert Electric function name to Re-frame view name.
   Note: This adds -view suffix. If you don't want the suffix, use electric-name->kebab-case"
  [electric-name]
  (let [name-str (name electric-name)
        ;; Convert CamelCase to kebab-case
        kebab-case (-> name-str
                       (str/replace #"([a-z])([A-Z])" "$1-$2")
                       (str/lower-case))]
    (symbol (str kebab-case "-view"))))

(defn- electric-name->kebab-case
  "Convert Electric CamelCase name to kebab-case (without -view suffix)"
  [electric-name]
  (-> (name electric-name)
      (str/replace #"([a-z])([A-Z])" "$1-$2")
      (str/lower-case)
      symbol))

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

(declare translate-dom-element)

(defn- component-call?
  "Check if a node represents a component call (not a dom/ or binding call)"
  [zloc]
  (when (z/list? zloc)
    (when-let [first-child (z/down zloc)]
      (and (z/sexpr-able? first-child)
           (symbol? (z/sexpr first-child))
           (let [sym-str (str (z/sexpr first-child))]
             (and (not (str/includes? sym-str "/"))
                  (not= sym-str "binding")
                  (Character/isUpperCase (first sym-str))))))))

(defn- translate-component-call
  "Translate a component call from (ComponentName args) to [component-name-view args]"
  [zloc]
  (when (component-call? zloc)
    (let [component-name (z/sexpr (z/down zloc))
          ;; Convert CamelCase to kebab-case and add -view suffix
          view-name (electric-name->view-name component-name)
          ;; Get all the arguments
          args (loop [child (z/right (z/down zloc))
                      result []]
                 (if child
                   (recur (z/right child)
                          (conj result (translate-dom-element child)))
                   result))]
      ;; Return as a vector with component name and args
      (into [view-name] args))))

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

    ;; Handle component calls like (LabelAndAmount ...)
    (component-call? zloc)
    (translate-component-call zloc)

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
  "Find the body expressions inside (e/client ...).
   Handles two cases:
   1. (e/client (binding [...] body...))
   2. (e/client body...)"
  [zloc]
  (when-let [client-node (z/find zloc z/next
                                 #(and (z/list? %)
                                       (= 'e/client (z/sexpr (z/down %)))))]
    (let [first-child (z/right (z/down client-node))]
      (cond
        ;; Case 1: (e/client (binding [...] body...))
        (and (z/list? first-child)
             (= 'binding (z/sexpr (z/down first-child))))
        (when-let [binding-vec (z/right (z/down first-child))]
          (loop [node (z/right binding-vec)
                 bodies []]
            (if node
              (recur (z/right node) (conj bodies node))
              bodies)))

        ;; Case 2: (e/client body...)
        :else
        (loop [node first-child
               bodies []]
          (if node
            (recur (z/right node) (conj bodies node))
            bodies))))))

(defn- extract-function-params
  "Extract parameters from an e/defn form"
  [defn-zloc]
  ;; defn-zloc is positioned at e/defn
  ;; Move right to get name, then right again to get params
  (when-let [name-zloc (z/right defn-zloc)]
    (when-let [params-zloc (z/right name-zloc)]
      (when (z/vector? params-zloc)
        (z/sexpr params-zloc)))))

(defn- find-dependencies
  "Find all function and variable dependencies in a form"
  [form]
  (let [deps (atom #{})
        ;; Stack of local binding contexts
        locals-stack (atom [#{}])
        ;; Track the current function being defined
        current-fn (atom nil)]

    ;; First pass - identify if this is a defn/def and get its name
    (when (and (seq? form)
               (contains? #{'defn 'def} (first form))
               (symbol? (second form)))
      (reset! current-fn (second form)))

    ;; For defn, add parameters to initial locals
    (when (and (seq? form)
               (= 'defn (first form))
               (vector? (nth form 2 nil)))
      (let [params (nth form 2)]
        ;; Extract all parameter names (including from destructuring)
        (letfn [(extract-params [p]
                  (cond
                    (symbol? p) (when (not= p '&) #{p})
                    (vector? p) (reduce into #{} (map extract-params p))
                    (map? p) (reduce into #{}
                                     (concat
                                      (map extract-params (vals p))
                                      (when-let [as (:as p)] [as])
                                      (when-let [keys (:keys p)] keys)
                                      (when-let [strs (:strs p)] (map symbol strs))
                                      (when-let [syms (:syms p)] syms)))
                    :else #{}))]
          (swap! locals-stack conj (extract-params params)))))

    (letfn [(current-locals []
              (reduce into #{} @locals-stack))

            (analyze-form [x]
              (cond
                ;; Track let/loop bindings
                (and (seq? x)
                     (contains? #{'let 'loop} (first x))
                     (vector? (second x)))
                (let [bindings (second x)
                      new-locals (atom #{})]
                  ;; Extract binding names
                  (doseq [[binding _] (partition 2 bindings)]
                    (cond
                      (symbol? binding) (swap! new-locals conj binding)
                      (vector? binding) (doseq [b binding]
                                          (when (symbol? b)
                                            (swap! new-locals conj b)))
                      (map? binding) (doseq [[k v] binding]
                                       (when (and (= k :as) (symbol? v))
                                         (swap! new-locals conj v)))))
                  ;; Push new locals context
                  (swap! locals-stack conj @new-locals)
                  ;; Analyze the binding values and body
                  (doseq [[_ val] (partition 2 bindings)]
                    (walk/prewalk analyze-form val))
                  (doseq [body-form (drop 2 x)]
                    (walk/prewalk analyze-form body-form))
                  ;; Pop locals context
                  (swap! locals-stack pop)
                  x)

                ;; Component calls in vectors
                (and (vector? x)
                     (pos? (count x))
                     (symbol? (first x))
                     (not= (first x) @current-fn)
                     (not ((current-locals) (first x))))
                (do (swap! deps conj (first x))
                    x)

                ;; Function calls (including namespaced like r-ui/pixelate)
                (and (seq? x)
                     (symbol? (first x))
                     (not= (first x) @current-fn)
                     (not ((current-locals) (first x)))
                     (not (contains? #{'defn 'def 'let 'let* 'fn 'fn* 'loop 'loop*
                                       'binding 'if 'when 'cond 'or 'and 'not '=
                                       'str '+ '- '* '/ 'into 'conj 'assoc 'dissoc
                                       'vec 'list 'map 'filter 'reduce 'apply 'partial
                                       'comp 'identity 'do 'quote 'var 'recur} (first x))))
                (do (swap! deps conj (first x))
                    x)

                ;; Bare symbol references (not namespaced)
                (and (symbol? x)
                     (not= x @current-fn)
                     (not (namespace x))
                     (not ((current-locals) x))
                     (not (contains? #{'defn 'def 'let 'let* 'fn 'fn* 'loop 'loop*
                                       'binding 'if 'when 'cond 'or 'and 'not '=
                                       'str '+ '- '* '/ 'true 'false 'nil '& 'do
                                       'quote 'var 'throw 'try 'catch 'finally 'new
                                       'set! 'recur 'ns 'require} x)))
                (do (swap! deps conj x)
                    x)

                :else x))]

      (walk/prewalk analyze-form form))

    @deps))

(defn- create-view-function
  "Create a Re-frame view function from Electric body expressions"
  [electric-name params body-zlocs]
  ;; Use the appropriate naming convention
  (let [view-name (electric-name->view-name electric-name)
        translated-bodies (map translate-dom-element body-zlocs)
        view-form (if (= 1 (count translated-bodies))
                    ;; Single element - no React Fragment needed
                    (list 'defn view-name params
                          (first translated-bodies))
                    ;; Multiple elements - wrap in React Fragment
                    (list 'defn view-name params
                          (into [:<>] translated-bodies)))]
    {:view view-form
     :name view-name
     :deps (find-dependencies view-form)}))

(defn- topological-sort
  "Sort views by their dependencies"
  [views]
  (let [;; Create a map of name to view
        view-map (into {} (map (juxt :name identity) views))
        ;; Track visited and result
        visited (atom #{})
        result (atom [])]

    (letfn [(visit [view-name]
              (when-not (@visited view-name)
                (swap! visited conj view-name)
                (when-let [view (view-map view-name)]
                  ;; Visit dependencies first
                  (doseq [dep (:deps view)]
                    (visit dep))
                  ;; Then add this view
                  (swap! result conj view))))]

      ;; Visit all views
      (doseq [view views]
        (visit (:name view)))

      ;; Add topo-sort numbers
      (map-indexed (fn [idx view]
                     (assoc view :topo-sort (inc idx)))
                   @result))))

(defn- translate-dom-forms
  "Translate a sequence of DOM forms (or a single form) to Hiccup.
   Returns a canonical AST structure."
  [dom-forms]
  (let [;; Check if it's a single DOM element
        single-dom? (and (seq? dom-forms)
                         (symbol? (first dom-forms))
                         (str/starts-with? (str (first dom-forms)) "dom/"))
        ;; Normalize to a sequence of forms
        forms (if single-dom?
                [dom-forms]
                dom-forms)
        ;; Convert each form to a zipper and translate
        translated (mapv (fn [form]
                          (let [zloc (z/of-string (pr-str form))]
                            (translate-dom-element zloc)))
                        forms)
        ;; Return single element or wrap multiple in fragment
        view (if (= 1 (count translated))
               (first translated)
               (into [:<>] translated))]
    {:view view
     :name 'anonymous
     :deps (find-dependencies view)}))

(defn- canonicalize-views
  "Ensure all views are in canonical form"
  [views]
  (mapv (fn [v]
          (if (map? v)
            v
            ;; Convert simple form to canonical
            {:view v
             :name (second v)  ; Extract name from (defn name ...)
             :deps (find-dependencies v)}))
        views))

(defn extract-simple-forms
  "Extract just the view forms from canonical AST structures.
   Given a vector of {:view form, :name symbol, :deps set, :topo-sort n} maps,
   returns a vector of just the view forms."
  [canonical-views]
  (mapv :view canonical-views))

(defn write-forms-to-file!
  "Write AST forms to a file with the given namespace.
   Expects forms to be canonical AST structures with :view, :name, :deps, :topo-sort keys."
  [ns-name ast-forms]
  (let [ns-parts (str/split (str ns-name) #"\.")
        ;; Get the base namespace (e.g., "reframe-output")
        base-ns (first ns-parts)
        ;; Create the nested directory structure
        dir-parts (mapv #(str/replace % "-" "_") ns-parts)
        ;; The file path includes both the hyphenated and underscored versions
        file-path (str base-ns "/" (apply str (interpose "/" dir-parts)) ".cljs")
        ;; Format namespace with require on new line
        ns-form-str (if (str/ends-with? ns-name ".views")
                       (str "(ns " ns-name "\n  (:require\n   [restaurant.ui :as r-ui]))")
                       (str "(ns " ns-name ")"))
        ;; Sort forms by topo-sort if they have it
        sorted-forms (if (and (seq ast-forms)
                              (:topo-sort (first ast-forms)))
                       (sort-by :topo-sort ast-forms)
                       ast-forms)
        ;; Extract just the view forms
        view-forms (extract-simple-forms sorted-forms)
        ;; Format each form properly
        format-form (fn [form]
                      (let [formatted
                            (if (and (seq? form)
                                     (= 'defn (first form))
                                     (symbol? (second form)))
                              ;; Special handling for defn to keep name on same line
                              (let [[_ name params & body] form
                                    formatted (with-out-str
                                                (binding [pp/*print-right-margin* 80]
                                                  (pp/pprint (cons 'defn (cons name (cons params body))))))]
                                ;; Replace the first newline after defn with a space
                                (str/replace-first formatted #"^(\(defn)\n\s*" "$1 "))
                              ;; Use regular pprint for other forms
                              (with-out-str (pp/pprint form)))]
                        ;; Trim trailing newline
                        (str/trim-newline formatted)))
        ;; Build the file content
        file-content (str ns-form-str
                          "\n\n"  ; Blank line after namespace
                          (str/join "\n\n" (map format-form view-forms)))
        ;; Create directory if needed
        file (clojure.java.io/file file-path)
        parent-dir (.getParentFile file)]
    (when parent-dir
      (.mkdirs parent-dir))
    (spit file-path file-content)))

(defn translate
  "Translate Electric code to Re-frame components.
   Accepts either:
   - A file path and starting function: \"path/to/file.cljc\" \"Main\"
   - An e/defn form: (e/defn Name [...] ...)
   - A single DOM form: (dom/div ...)
   - Multiple DOM forms: ((dom/h1 ...) (dom/p ...))

   Returns a map with :views, :events, and :subs keys.
   The :views value is a vector of canonical AST maps, each containing:
   - :view   - the actual Clojure form
   - :name   - the function name (symbol)
   - :deps   - set of dependencies
   - :topo-sort - topological sort order (when applicable)

   Use extract-simple-forms to get just the view forms.

   Optional output-ns parameter writes the forms to files."
  ([first-arg]
   (translate first-arg nil nil))
  ([first-arg second-arg]
   (if (string? first-arg)
     ;; File path case - second arg is starting function
     (translate first-arg second-arg nil)
     ;; Electric code case - second arg is output-ns
     (translate first-arg nil second-arg)))
  ([file-path-or-code starting-fn-name output-ns]
   (let [;; Determine if we're dealing with a file or direct code
         is-file-path? (string? file-path-or-code)

         ;; Process based on input type - always work with canonical form
         canonical-result (if is-file-path?
                            ;; FILE PATH CASE
                            (let [file-path file-path-or-code
                                  zloc (z/of-file file-path)
                                  starting-sym (symbol starting-fn-name)
                                  ;; First, collect ALL forms in the file for dependency resolution
                                  all-forms-map (loop [loc zloc
                                                       result {}]
                                                  (if-let [form-loc (z/find-tag loc z/next :list)]
                                                    (let [first-sym (when (z/down form-loc)
                                                                      (z/sexpr (z/down form-loc)))]
                                                      (cond
                                                        ;; e/defn form - translate to view
                                                        (= 'e/defn first-sym)
                                                        (let [name-loc (z/right (z/down form-loc))
                                                              electric-name (z/sexpr name-loc)
                                                              params (extract-function-params (z/down form-loc))
                                                              body-locs (find-client-binding-body form-loc)
                                                              view-data (create-view-function electric-name (or params []) (or body-locs []))]
                                                          (recur (z/right form-loc) (assoc result (:name view-data) view-data)))

                                                        ;; Regular defn or def - include as-is
                                                        (or (= 'defn first-sym) (= 'def first-sym))
                                                        (let [form (z/sexpr form-loc)
                                                              name-sym (second form)
                                                              deps (find-dependencies form)]
                                                          (recur (z/right form-loc)
                                                                 (assoc result name-sym {:view form
                                                                                         :name name-sym
                                                                                         :deps deps})))

                                                        :else
                                                        (recur (z/next form-loc) result)))
                                                    result))
                                  ;; Now collect only the starting function and its dependencies
                                  ;; Use kebab-case version of the starting function name
                                  starting-view-name (electric-name->view-name starting-sym)
                                  collected (atom #{})
                                  to-collect (atom [starting-view-name])]

                              ;; Collect transitively
                              (while (seq @to-collect)
                                (let [current (first @to-collect)]
                                  (swap! to-collect rest)
                                  (when-not (@collected current)
                                    (swap! collected conj current)
                                    (when-let [form-data (all-forms-map current)]
                                      (swap! to-collect into (:deps form-data))))))

                              ;; Get the forms we actually need
                              (let [needed-forms (filter #(@collected (:name %)) (vals all-forms-map))
                                    ;; Apply topological sort
                                    sorted-views (topological-sort needed-forms)]
                                {:views sorted-views
                                 :events []
                                 :subs []}))

                            ;; ELECTRIC CODE CASE
                            (let [electric-code file-path-or-code
                                  code-str (pr-str electric-code)
                                  zloc (z/of-string code-str)
                                  ;; Check if it's an e/defn form
                                  result (if-let [defn-zloc (z/find-value zloc z/next 'e/defn)]
                                           ;; Handle e/defn form
                                           (let [;; Get the function name (move right from e/defn)
                                                 name-zloc (z/right defn-zloc)
                                                 electric-name (z/sexpr name-zloc)
                                                 ;; Get the parameters
                                                 params (extract-function-params defn-zloc)
                                                 ;; Find body expressions
                                                 body-zlocs (find-client-binding-body (z/up defn-zloc))
                                                 ;; Create view with metadata
                                                 view-data (create-view-function electric-name (or params []) (or body-zlocs []))]

                                             {:views [(assoc view-data :topo-sort 1)]
                                              :events []
                                              :subs []})
                                           ;; Not an e/defn - assume it's DOM form(s)
                                           (let [view-data (translate-dom-forms electric-code)]
                                             {:views [(assoc view-data :topo-sort 1)]
                                              :events []
                                              :subs []}))]
                              result))]

     ;; If output-ns is provided, write the forms to files
     (when output-ns
       (when (seq (:views canonical-result))
         (write-forms-to-file! (str output-ns ".views") (:views canonical-result)))
       (when (seq (:events canonical-result))
         (write-forms-to-file! (str output-ns ".events") (:events canonical-result)))
       (when (seq (:subs canonical-result))
         (write-forms-to-file! (str output-ns ".subs") (:subs canonical-result))))

     ;; Return canonical AST structure
     canonical-result)))

(comment
  ;; Returns canonical AST structure
  (translate "electric-src/electric_starter_app/main.cljc" "Main" "reframe-output")
  ;; => {:views [{:view (defn ...), :name main-view, :deps #{...}, :topo-sort 1} ...]}

  ;; To get just the forms:
  (extract-simple-forms (:views (translate "electric-src/electric_starter_app/main.cljc" "Main")))
  ;; => [(defn main-view ...) ...]
  )
