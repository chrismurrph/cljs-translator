(ns translator.translator
  (:require [rewrite-clj.zip :as z]
            [rewrite-clj.node :as n]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.walk :as walk]
            [cljfmt.core :as fmt]))

(defn electric-name->view-name
  "Convert Electric function name to Re-frame view name.
   e.g. TodoItem -> todo-item-view"
  [electric-name]
  (-> (name electric-name)
      (str/replace #"([a-z])([A-Z])" "$1-$2")
      str/lower-case
      (str "-view")
      symbol))

(defn electric-name->kebab-case
  "Convert Electric function name to kebab case.
   e.g. TodoItem -> todo-item"
  [electric-name]
  (-> (name electric-name)
      (str/replace #"([a-z])([A-Z])" "$1-$2")
      str/lower-case
      symbol))

(defn dom-element?
  "Check if a zipper location is a DOM element call"
  [zloc]
  (and (z/list? zloc)
       (when-let [first-child (z/down zloc)]
         (let [sym (z/sexpr first-child)]
           (and (symbol? sym)
                (or (str/starts-with? (str sym) "dom/")
                    (str/starts-with? (str sym) "svg/"))))))) 

(defn extract-tag-name
  "Extract HTML tag name from dom/element or svg/element symbol"
  [dom-symbol]
  (let [s (str dom-symbol)]
    (cond
      (str/starts-with? s "dom/") (keyword (subs s 4))
      (str/starts-with? s "svg/") (keyword (subs s 4))
      :else (throw (ex-info "Unknown element namespace" {:symbol dom-symbol}))))) 

(declare translate-electric-form) 

(defn component-call?
  "Check if a form is a component call (starts with uppercase)"
  [zloc]
  (and (z/list? zloc)
       (when-let [first-child (z/down zloc)]
         (let [sym (z/sexpr first-child)]
           (and (symbol? sym)
                (Character/isUpperCase (first (name sym)))))))) 

(defn translate-component-call
  "Translate a component call from (ComponentName args) to [component-name args]"
  [zloc]
  (when (component-call? zloc)
    (let [component-name (z/sexpr (z/down zloc))
          view-name (electric-name->kebab-case component-name)
          args (loop [child (z/right (z/down zloc))
                      result []]
                 (if child
                   (recur (z/right child)
                          (conj result (translate-electric-form child)))
                   result))]
      (into [view-name] args))))

(defn translate-dom-text
  "Extract text from (dom/text \"...\") node"
  [zloc]
  (when (z/list? zloc)
    (when-let [first-child (z/down zloc)]
      (when (= 'dom/text (z/sexpr first-child))
        (when-let [text-node (z/right first-child)]
          (z/sexpr text-node))))))

(defn translate-dom-props
  "Extract props from (dom/props {...}) node"
  [zloc]
  (when (z/list? zloc)
    (when-let [first-child (z/down zloc)]
      (when (= 'dom/props (z/sexpr first-child))
        (when-let [props-node (z/right first-child)]
          (z/sexpr props-node))))))

(def mutation-ns-to-event-ns
  "Map mutation namespaces to their corresponding event namespaces.
   For special cases that don't follow the simple muts->events pattern."
  {"wc-muts-till" 'wc-till-events
   "r-muts" 'r-events})

(def alias-to-namespace
  "Configuration map for converting namespace aliases to full namespaces.
   Used when translating Electric mutation namespaces to Re-frame event namespaces."
  {'r-events 'restaurant.events
   'wc-till-events 'restaurant.with-customer.till.events
   ;; Add more mappings as needed
   })

(defn transform-mutation-to-dispatch
  "Transform a mutation call to a Re-frame dispatch.
   Handles patterns like:
   (wc-state/wc-mutation wc-muts-till/receive-note args...)
   -> (dispatch [::wc-till-events/receive-note args...])"
  [mutation-form]
  (when (and (seq? mutation-form)
             (symbol? (first mutation-form))
             (str/includes? (str (first mutation-form)) "mutation"))
    (let [actual-mutation (second mutation-form)
          mutation-args (drop 2 mutation-form)]
      (when (and (symbol? actual-mutation)
                 (namespace actual-mutation))
        (let [mutation-ns (namespace actual-mutation)
              new-ns (or (get mutation-ns-to-event-ns mutation-ns)
                        (symbol (str/replace mutation-ns "muts" "events")))
              event-name (name actual-mutation)
              full-ns (get alias-to-namespace new-ns)]
          (if full-ns
            (list 'dispatch (into [(keyword (name full-ns) event-name)] mutation-args))
            (throw (ex-info (str "Unknown namespace alias: " new-ns 
                                 ". Please add a mapping to alias-to-namespace configuration.")
                            {:alias new-ns
                             :mutation-ns mutation-ns
                             :available-aliases (keys alias-to-namespace)})))))))) 

 (defn transform-mutations-in-form
  "Walk through a form and transform any mutation calls to dispatches"
  [form]
  (walk/prewalk
   (fn [x]
     (if (and (seq? x)
              (symbol? (first x))
              (str/includes? (str (first x)) "mutation"))
       (or (transform-mutation-to-dispatch x) x)
       x))
   form)) 

 (defn translate-dom-event
  "Extract event handler from (dom/On \"event\" handler) node and return props map"
  [zloc]
  (when (z/list? zloc)
    (when-let [first-child (z/down zloc)]
      (when (= 'dom/On (z/sexpr first-child))
        (when-let [event-name-node (z/right first-child)]
          (when-let [handler-node (z/right event-name-node)]
            (let [event-name (z/sexpr event-name-node)
                  handler (z/sexpr handler-node)
                  rf-event-key (keyword (str "on-" event-name))]
              (if (and (seq? handler)
                       (or (= 'fn (first handler))
                           (= 'fn* (first handler))))
                (let [fn-body (nth handler 2)]
                  (if-let [dispatch-form (transform-mutation-to-dispatch fn-body)]
                    {rf-event-key (list 'fn [] dispatch-form)}
                    {rf-event-key handler}))
                {rf-event-key handler}))))))))

(defn translate-electric-form
  "Translate a single Electric form to Hiccup"
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
          ;; Process children and collect props and events separately
          {:keys [props events children]} 
          (loop [child (z/right (z/down zloc))
                 result {:props nil :events {} :children []}]
            (if child
              (let [;; Check if it's dom/props
                    dom-props (translate-dom-props child)
                    ;; Check if it's dom/On
                    dom-event (translate-dom-event child)
                    ;; Otherwise it's a regular child
                    translated-child (when (and (not dom-props) (not dom-event))
                                       (translate-electric-form child))]
                (recur (z/right child)
                       (cond-> result
                         dom-props (assoc :props dom-props)
                         dom-event (update :events merge dom-event)
                         translated-child (update :children conj translated-child))))
              result))
          ;; Filter out nils from children
          children (remove nil? children)
          ;; Merge props and events
          all-props (merge props events)]

      (cond
        ;; No children and no props
        (and (empty? children) (empty? all-props))
        [tag-name]

        ;; Has props but no children
        (and (empty? children) (seq all-props))
        [tag-name all-props]

        ;; Has children but no props
        (and (seq children) (empty? all-props))
        (into [tag-name] children)

        ;; Has both props and children
        :else
        (into [tag-name all-props] children)))

    ;; Handle let forms - translate the body
    (and (z/list? zloc)
         (= 'let (z/sexpr (z/down zloc))))
    (let [let-sym-zloc (z/down zloc)
          bindings-zloc (z/right let-sym-zloc)
          ;; Move to the body forms after bindings
          body-start (z/right bindings-zloc)
          ;; Translate all body forms
          translated-body (loop [current body-start
                                result []]
                           (if current
                             (recur (z/right current)
                                    (conj result (translate-electric-form current)))
                             result))
          ;; Filter out nils
          translated-body (remove nil? translated-body)]
      ;; Reconstruct the let form with translated body
      (if (= 1 (count translated-body))
        `(~'let ~(z/sexpr bindings-zloc) ~@translated-body)
        `(~'let ~(z/sexpr bindings-zloc) ~@translated-body)))

    ;; Handle if forms - translate the branches
    (and (z/list? zloc)
         (= 'if (z/sexpr (z/down zloc))))
    (let [if-sym-zloc (z/down zloc)
          condition-zloc (z/right if-sym-zloc)
          then-zloc (z/right condition-zloc)
          else-zloc (z/right then-zloc)
          ;; Translate branches
          translated-then (translate-electric-form then-zloc)
          translated-else (when else-zloc (translate-electric-form else-zloc))]
      (if translated-else
        `(~'if ~(z/sexpr condition-zloc) ~translated-then ~translated-else)
        `(~'if ~(z/sexpr condition-zloc) ~translated-then)))

    ;; Handle when forms - translate the body
    (and (z/list? zloc)
         (= 'when (z/sexpr (z/down zloc))))
    (let [when-sym-zloc (z/down zloc)
          condition-zloc (z/right when-sym-zloc)
          ;; Get all body forms after condition
          body-zlocs (loop [current (z/right condition-zloc)
                           result []]
                      (if current
                        (recur (z/right current) (conj result current))
                        result))
          ;; Translate all body forms
          translated-body (mapv translate-electric-form body-zlocs)
          ;; Filter out nils
          translated-body (remove nil? translated-body)]
      ;; Return when form with translated body
      (if (= 1 (count translated-body))
        `(~'when ~(z/sexpr condition-zloc) ~@translated-body)
        `(~'when ~(z/sexpr condition-zloc) ~@translated-body)))

    ;; Handle e/for forms - translate to regular for
    (and (z/list? zloc)
         (= 'e/for (z/sexpr (z/down zloc))))
    (let [for-sym-zloc (z/down zloc)
          bindings-zloc (z/right for-sym-zloc)
          bindings (z/sexpr bindings-zloc)
          ;; Handle e/diff-by in bindings
          clean-bindings (if (and (vector? bindings)
                                  (>= (count bindings) 2))
                           (let [[binding-sym expr] bindings]
                             ;; Check if expr is (e/diff-by ...)
                             (if (and (seq? expr)
                                     (= 'e/diff-by (first expr)))
                               ;; Replace e/diff-by with just the collection
                               [binding-sym (nth expr 2)]
                               bindings))
                           bindings)
          ;; Get body forms
          body-zlocs (loop [current (z/right bindings-zloc)
                           result []]
                      (if current
                        (recur (z/right current) (conj result current))
                        result))
          ;; Translate body
          translated-body (mapv translate-electric-form body-zlocs)
          ;; Filter out nils
          translated-body (remove nil? translated-body)
          ;; Extract the key for React
          ;; For simple bindings like [x coll], use x as key
          ;; For destructured bindings like [[k v] map], use first element
          key-expr (if (vector? clean-bindings)
                    (let [binding-sym (first clean-bindings)]
                      (if (vector? binding-sym)
                        `(~'str ~(first binding-sym))
                        `(~'str ~binding-sym)))
                    `(~'str "item"))]
      ;; Return for form with the key metadata
      ;; We use vary-meta to attach metadata
      (if (= 1 (count translated-body))
        (let [body-elem (first translated-body)]
          `(~'for ~clean-bindings
             ~(vary-meta body-elem assoc :key key-expr)))
        `(~'for ~clean-bindings
           ~(vary-meta (into [:<>] translated-body) assoc :key key-expr))))
    ;; Check for unsupported special forms that contain DOM elements
    (and (z/list? zloc)
         (when-let [first-child (z/down zloc)]
           (let [form-type (z/sexpr first-child)]
             (contains? #{'when-not 'when-let 'when-some
                          'cond 'case 'condp
                          'doseq 'while
                          'loop 'recur
                          'try 'catch 'finally 'throw
                          '->> '-> 'as-> 'some-> 'some->>
                          'and 'or} form-type))))
    (let [form-type (z/sexpr (z/down zloc))
          form-str (z/string zloc)
          contains-dom? (str/includes? form-str "dom/")]
      (if contains-dom?
        (throw (ex-info (str "Unsupported form '" form-type "' containing DOM elements. "
                             "The translator currently supports 'let', 'if', 'when', and 'e/for' for control flow. "
                             "Please refactor to use supported forms.")
                        {:unsupported-form form-type
                         :form (z/sexpr zloc)
                         :supported-forms #{'let 'if 'when 'e/for}}))
        (z/sexpr zloc)))

    ;; Check for mutation calls anywhere
    (and (z/list? zloc)
         (when-let [first-child (z/down zloc)]
           (let [sym (z/sexpr first-child)]
             (and (symbol? sym)
                  (str/includes? (str sym) "mutation")))))
    (let [form (z/sexpr zloc)]
      (or (transform-mutation-to-dispatch form) form))

    ;; For other nodes, try to get sexpr
    (z/sexpr-able? zloc)
    (z/sexpr zloc)

    :else
    nil))

(defn find-client-binding-body
  "Find body expressions inside (e/client (binding [...] body...)) or (e/client body...)"
  [edefnzloc]
  (when-let [client-zloc (z/find-value edefnzloc z/next 'e/client)]
    (when-let [client-form (z/up client-zloc)]
      ;; Check if next element is a binding form
      (let [first-in-client (z/down (z/right client-zloc))]
        (if (and first-in-client
                 (= 'binding (z/sexpr first-in-client)))
          ;; Has binding - get body after binding vector
          (let [binding-form (z/up first-in-client)
                binding-vec (z/right (z/down binding-form))]
            ;; Everything after binding vec is body
            (loop [node (z/right binding-vec)
                   bodies []]
              (if node
                (recur (z/right node) (conj bodies node))
                bodies)))
          ;; No binding - everything after e/client is body
          (loop [node (z/right client-zloc)
                 bodies []]
            (if node
              (recur (z/right node) (conj bodies node))
              bodies)))))))

(defn extract-function-params
  "Extract parameter vector from an e/defn zipper location"
  [defnzloc]
  (when-let [name-zloc (z/right defnzloc)]
    (when-let [params-zloc (z/right name-zloc)]
      (z/sexpr params-zloc))))

(defn find-dependencies
  "Find all user-defined function dependencies in a form, excluding:
   - Local bindings and parameters
   - Electric framework functions (dom/*, e/*, svg/*)
   - Clojure core functions
   - Special forms (def, defn, let, binding, etc.)"
  [form]
  (let [bindings (atom #{})
        deps (atom #{})
        ;; Common Clojure special forms and core functions to exclude
        core-forms #{'def 'defn 'let 'let* 'fn 'fn* 'loop 'loop*
                     'binding 'if 'when 'cond 'or 'and 'not '=
                     'str '+ '- '* '/ 'into 'conj 'assoc 'dissoc
                     'vec 'list 'map 'filter 'reduce 'apply 'partial
                     'comp 'identity 'do 'quote 'var 'recur 'throw
                     'try 'catch 'finally 'new 'set! 'ns 'require
                     'read-string 'true 'false 'nil}]

    ;; Extract parameters from the form if it's a function definition
    (when (and (seq? form)
               (or (= 'defn (first form)) (= 'e/defn (first form)))
               (vector? (nth form 2 nil)))
      (doseq [param (nth form 2)]
        (when (symbol? param)
          (swap! bindings conj param))))

    (walk/prewalk
     (fn [x]
       (cond
         ;; Track local bindings in let/loop
         (and (seq? x)
              (contains? #{'let 'loop} (first x))
              (vector? (second x)))
         (do
           (doseq [binding (take-nth 2 (second x))]
             (when (symbol? binding)
               (swap! bindings conj binding)))
           x)

         ;; Track binding form
         (and (seq? x)
              (= 'binding (first x))
              (vector? (second x)))
         (do
           (doseq [binding (take-nth 2 (second x))]
             (when (symbol? binding)
               (swap! bindings conj binding)))
           x)

         ;; Function calls - (function-name ...)
         (and (seq? x)
              (symbol? (first x))
              (not (@bindings (first x)))
              (not (core-forms (first x)))
              ;; Exclude Electric/DOM framework functions
              (not (str/starts-with? (str (first x)) "dom/"))
              (not (str/starts-with? (str (first x)) "e/"))
              (not (str/starts-with? (str (first x)) "svg/"))
              ;; Include user-defined functions (either no namespace or user namespace)
              (or (not (namespace (first x)))
                  (and (namespace (first x))
                       (not (contains? #{"dom" "e" "svg" "clojure.core"} (namespace (first x)))))))
         (do
           (swap! deps conj (first x))
           x)

         ;; Bare symbol references - only if they look like user-defined constants
         (and (symbol? x)
              (not (@bindings x))
              (not (core-forms x))
              ;; Only include if it's not namespaced
              (not (namespace x))
              ;; And it looks like a user constant (not single letters which are often params)
              (> (count (str x)) 1))
         (do
           (swap! deps conj x)
           x)

         :else x))
     form)
    ;; Remove self-references
    (let [form-name (when (and (seq? form)
                                (or (= 'defn (first form))
                                    (= 'def (first form))
                                    (= 'e/defn (first form))))
                      (second form))]
      (disj @deps form-name))))

(defn create-view-function
  "Create a Re-frame view function from Electric function components"
  [electric-name params body-zlocs is-main?]
  (let [view-name (if is-main?
                    (electric-name->view-name electric-name)
                    (electric-name->kebab-case electric-name))
        body-elements (mapv translate-electric-form body-zlocs) 
        body-elements (remove nil? body-elements)
        view-body (if (= 1 (count body-elements))
                    (first body-elements)
                    (into [:<>] body-elements))
        view-form (if (empty? params)
                    `(~'defn ~view-name []
                      ~view-body)
                    `(~'defn ~view-name ~params
                      ~view-body))
        deps (find-dependencies view-form)]
    {:view view-form
     :name view-name
     :deps deps}))

(defn topological-sort
  "Sort a collection of {:name ... :deps ...} maps topologically.
   Items with no dependencies come first."
  [items]
  (let [;; Create a map of name -> item for easy lookup
        item-map (into {} (map (juxt :name identity) items))
        ;; Track visited nodes
        visited (atom #{})
        ;; Track nodes in current path (for cycle detection)
        in-path (atom #{})
        ;; Result accumulator
        result (atom [])

        ;; DFS visit function
        visit (fn visit [name]
                (when-not (@visited name)
                  (when (@in-path name)
                    (throw (ex-info "Cycle detected in dependencies" {:node name})))
                  (swap! in-path conj name)

                  ;; Visit all dependencies first
                  (when-let [item (item-map name)]
                    (doseq [dep (:deps item)]
                      (when (item-map dep) ; Only visit if dep is in our items
                        (visit dep))))

                  (swap! in-path disj name)
                  (swap! visited conj name)

                  ;; Add to result if it's one of our items
                  (when-let [item (item-map name)]
                    (swap! result conj (assoc item :topo-sort (inc (count @result)))))))]

    ;; Visit all items
    (doseq [{:keys [name]} items]
      (visit name))

    @result))

(defn translate-dom-forms
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
                            (translate-electric-form zloc)))
                        forms)
        ;; Return single element or wrap multiple in fragment
        view (if (= 1 (count translated))
               (first translated)
               (into [:<>] translated))]
    {:view view
     :name 'anonymous
     :deps (find-dependencies view)}))

(defn canonicalize-views
  "Ensure all views are in canonical AST format with :view, :name, :deps keys"
  [views]
  (mapv (fn [v]
          (if (map? v)
            v
            ;; Convert raw form to canonical format
            {:view v
             :name (if (and (seq? v) (= 'defn (first v)))
                     (second v)
                     'anonymous)
             :deps (find-dependencies v)}))
        views))

(defn extract-simple-forms
  "Extract just the view forms from canonical AST structures"
  [canonical-views]
  (mapv :view canonical-views))

(defn write-forms-to-file!
  "Write a collection of AST forms to a file with proper formatting using zprint.
   Forms should have :view, :name, and optionally :topo-sort keys."
  [ns-name ast-forms]
  (let [;; Convert hyphenated namespace to underscored subdirectory
        ;; e.g. "reframe-output.views" -> "reframe-output/reframe_output/views.cljs"
        [base-dir sub-ns] (str/split ns-name #"\." 2)
        sub-dir (str/replace base-dir #"-" "_")
        file-path (str base-dir "/" sub-dir "/" sub-ns ".cljs")

        ;; Extract unique namespace requires
        requires (atom #{})
        _ (walk/prewalk
           (fn [form]
             (cond
               ;; Check for namespaced symbols
               (and (symbol? form)
                    (namespace form))
               (do (swap! requires conj (symbol (namespace form)))
                   form)
               
               ;; Check for namespaced keywords (like :restaurant.events/take-out)
               (and (keyword? form)
                    (namespace form)
                    ;; Only add if it looks like a real namespace (contains a dot)
                    ;; This filters out CSS-style keywords like :qr/body-7
                    (str/includes? (namespace form) "."))
               (do (swap! requires conj (symbol (namespace form)))
                   form)
               
               ;; Check for dispatch calls
               (and (symbol? form)
                    (= 'dispatch form))
               (do (swap! requires conj 're-frame.core)
                   form)
               
               :else form))
           (map :view ast-forms)) 

        ;; Build the namespace form
        ;; Build the namespace form
        ;; Build the namespace form
        ns-form (if (empty? @requires)
                  `(~'ns ~(symbol (str base-dir "." sub-ns)))
                  `(~'ns ~(symbol (str base-dir "." sub-ns))
                    (:require
                     ~@(sort (map (fn [req]
                                    (cond
                                      (= req 're-frame.core)
                                      '[re-frame.core :refer [dispatch]]
                                      
                                      ;; For event namespaces, just require without :as
                                      (and (symbol? req)
                                           (str/ends-with? (str req) ".events"))
                                      [(symbol req)]
                                      
                                      ;; Default case - use :as
                                      :else
                                      [(symbol req) :as (symbol req)]))
                                  @requires))))) 

        ;; Sort forms if they have :topo-sort
        sorted-forms (if (every? #(contains? % :topo-sort) ast-forms)
                       (sort-by :topo-sort ast-forms)
                       ast-forms)

        ;; Extract just the :view from each form
        view-forms (mapv :view sorted-forms)

        ;; Use zprint for better formatting if available, otherwise fallback to cljfmt
        formatted-str (try
                        (require '[zprint.core :as zp])
                        (let [zprint-str (resolve 'zprint.core/zprint-file-str)
                              all-forms (into [ns-form] view-forms)]
                          (zprint-str (str/join "\n\n" (map pr-str all-forms))
                                      "" 
                                      {:parse-string? true
                                       :style :community}))
                        (catch Exception e
                          ;; Fallback to cljfmt
                          (let [all-forms (into [ns-form] view-forms)
                                unformatted-str (str/join "\n\n" (map pr-str all-forms))]
                            (fmt/reformat-string unformatted-str))))]

    ;; Ensure directory exists
    (let [file (clojure.java.io/file file-path)
          parent-dir (.getParentFile file)]
      (.mkdirs parent-dir))

    ;; Write the formatted content
    (spit file-path formatted-str)))

(defn read-file-forms
  "Read a file and extract Electric forms starting from a specific function and its dependencies.

   Returns a vector of {:form electric-form, :name symbol, :type keyword} in dependency order.
   Only includes the starting function and its transitive dependencies.

   Form types:
   - :e/defn - Electric function definitions
   - :defn - Regular Clojure functions
   - :def - Regular Clojure defs

   Example:
   (read-file-forms \"path/to/file.cljc\" \"Main\")
   ;; => [{:form (def customer-columns-xs [100 70 70]), :name customer-columns-xs, :type :def}
   ;;     {:form (defn generate-absolute-style ...), :name generate-absolute-style, :type :defn}
   ;;     {:form (e/defn LabelAndAmount ...), :name LabelAndAmount, :type :e/defn}
   ;;     {:form (e/defn Main ...), :name Main, :type :e/defn}]"
  [file-path starting-fn-name]
  ;; Try a different approach - read the file content and parse it manually
  (let [content (slurp file-path)
        ;; Use edamame to parse with reader conditionals
        forms (try
                (require '[edamame.core :as e])
                ((resolve 'edamame.core/parse-string-all) content {:features #{:cljs}
                                                                    :read-cond :allow
                                                                    :fn true}) 
                (catch Exception e
                  ;; Fallback to reading forms one by one
                  (println "edamame" {:e e})
                  (let [rdr (clojure.lang.LineNumberingPushbackReader.
                             (java.io.StringReader. content))]
                    (binding [*read-eval* false]
                      (loop [forms []]
                        (let [form (try (read rdr) (catch Exception e ::eof))]
                          (if (= ::eof form)
                            forms
                            (recur (conj forms form)))))))))

        starting-sym (symbol starting-fn-name)
        all-forms-map (atom {})

        ;; Process each form
        _ (doseq [form forms]
            (when (and (seq? form) (symbol? (first form)))
              (let [form-type (first form)]
                (when (contains? #{'e/defn 'defn 'def} form-type)
                  (let [name-sym (second form)]
                    (when (symbol? name-sym)
                      (swap! all-forms-map assoc name-sym
                             {:form form
                              :name name-sym
                              :type (keyword form-type)
                              :deps (find-dependencies form)})))))))

        ;; Now collect only the starting function and its dependencies
        collected (atom #{})
        to-collect (atom [starting-sym])]

    ;; Collect transitively
    (while (seq @to-collect)
      (let [current (first @to-collect)]
        (swap! to-collect rest)
        (when-not (@collected current)
          (swap! collected conj current)
          (when-let [form-data (@all-forms-map current)]
            (swap! to-collect into (:deps form-data))))))

    ;; Get the forms we need and apply topological sort
    (let [needed-forms (filter #(@collected (:name %)) (vals @all-forms-map))
          sorted (topological-sort needed-forms)]
      sorted)))




(defn translate
  "Translate Electric code to Re-frame components.

   Accepts either:
   - A vector of forms from read-file-forms (already sorted topologically)
   - An e/defn form: (e/defn Name [...] ...)
   - A single DOM form: (dom/div ...)
   - Multiple DOM forms: ((dom/h1 ...) (dom/p ...))

   When first arg is a vector:
   - Second arg is the starting function name (ignored since vector is pre-filtered)
   - Third arg is optional output-ns for file writing

   When first arg is a form:
   - Second arg is optional output-ns for file writing

   Returns a map with :views, :events, and :subs keys.
   The :views value is a vector of canonical AST maps, each containing:
   - :view   - the actual Clojure form
   - :name   - the function name (symbol)
   - :deps   - set of dependencies
   - :topo-sort - topological sort order (when applicable)

   Use extract-simple-forms to get just the view forms."
  ([forms-or-code]
   (translate forms-or-code nil nil))
  ([forms-or-code second-arg]
   (if (vector? forms-or-code)
     ;; Vector of forms case - second arg is starting function name (ignored)
     (translate forms-or-code second-arg nil)
     ;; Direct code case - second arg is output-ns
     (translate forms-or-code nil second-arg)))
  ([forms-or-code starting-fn-name output-ns]
   (let [;; Determine if we're dealing with a forms vector or direct code
         is-forms-vector? (vector? forms-or-code)

         ;; Process based on input type - always work with canonical form
         canonical-result (if is-forms-vector?
                            ;; FORMS VECTOR CASE - from read-file-forms
                            (let [;; Translate each form based on its type
                                  translated-views
                                  (mapv (fn [{:keys [form name type]}]
                                          (case type
                                            :e/defn
                                            ;; Extract the Electric function components
                                            (let [params (extract-function-params (z/down (z/of-string (pr-str form))))
                                                  ;; Find body - try e/client first, then direct body
                                                  form-zloc (z/of-string (pr-str form))
                                                  body-zlocs (or (find-client-binding-body form-zloc)
                                                                 ;; If no e/client, get body after params
                                                                 (let [defnzloc (z/find-value form-zloc z/next 'e/defn)
                                                                       name-zloc (z/right defnzloc)
                                                                       params-vec-zloc (z/right name-zloc)]
                                                                   (loop [node (z/right params-vec-zloc)
                                                                          bodies []]
                                                                     (if node
                                                                       (recur (z/right node) (conj bodies node))
                                                                       bodies))))]
                                              (create-view-function name (or params []) (or body-zlocs []) (= name (symbol starting-fn-name))))

                                            ;; Regular defn or def - include as-is
                                            (:defn :def)
                                            {:view (if (= type :defn)
                                                    (transform-mutations-in-form form)
                                                    form)
                                             :name name
                                             :deps (find-dependencies form)}))
                                        forms-or-code)]
                              {:views translated-views
                               :events []
                               :subs []})

                            ;; ELECTRIC CODE CASE - direct forms
                            (let [electric-code forms-or-code
                                  code-str (pr-str electric-code)
                                  zloc (z/of-string code-str)
                                  ;; Check if it's an e/defn form
                                  result (if-let [defnzloc (z/find-value zloc z/next 'e/defn)]
                                           ;; Handle e/defn form
                                           (let [;; Get the function name (move right from e/defn)
                                                 name-zloc (z/right defnzloc)
                                                 electric-name (z/sexpr name-zloc)
                                                 ;; Get the parameters
                                                 params (extract-function-params defnzloc)
                                                 ;; Find body expressions - first try e/client, then direct body
                                                 edefnform (z/up defnzloc)
                                                 body-zlocs (or (find-client-binding-body edefnform)
                                                                ;; If no e/client, get body after params
                                                                (let [params-zloc (z/find-value edefnform z/next electric-name)
                                                                      params-vec-zloc (z/right params-zloc)]
                                                                  (loop [node (z/right params-vec-zloc)
                                                                         bodies []]
                                                                    (if node
                                                                      (recur (z/right node) (conj bodies node))
                                                                      bodies))))
                                                 ;; Create view with metadata
                                                 view-data (create-view-function electric-name (or params []) (or body-zlocs []) true)]

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

; Removed unused translate-file function

(comment
  ;; Example usage:

  ;; First read forms from a file
  (def forms (read-file-forms "electric-src/electric_starter_app/main.cljc" "Main"))

  ;; Translate with file output (writes to reframe-output/reframe_output/*.cljs)
  (translate forms "Main" "reframe-output")

  ;; Just translate without writing
  (translate forms "Main")

  ;; Translate a single e/defn form
  (translate '(e/defn Test []
                (dom/div (dom/text "Hello"))))

  ;; Translate DOM forms directly
  (translate '((dom/h1 (dom/text "Title"))
               (dom/p (dom/text "Content"))))

  ;; Get simple forms from translation result
  (extract-simple-forms (:views (translate forms "Main")))
  )
