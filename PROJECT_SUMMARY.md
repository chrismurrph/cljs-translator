# PROJECT_SUMMARY.md

## Overview
This project is an AI-assisted tool for translating Electric Clojure programs into Re-frame applications. The translation converts Electric's reactive DOM API into Re-frame's Hiccup-based view syntax, along with any necessary events and subscriptions.

## Key File Paths

### Source Files
- `electric-src/electric_starter_app/main.cljc` - Electric source code to be translated
- `reframe-examples/reframe_examples/views.cljs` - Expected Re-frame view output
- `reframe-examples/reframe_examples/events.cljs` - Expected Re-frame events output
- `reframe-examples/reframe_examples/subs.cljs` - Expected Re-frame subscriptions output
- `src/translator/translator.clj` - Main translation implementation
- `src/translator/translation_test.clj` - Test suite

### Output Files (Generated)
- `reframe-output/reframe_output/views.cljs` - Generated Re-frame views
- `reframe-output/reframe_output/events.cljs` - Generated Re-frame events
- `reframe-output/reframe_output/subs.cljs` - Generated Re-frame subscriptions

### Configuration
- `deps.edn` - Project dependencies and development aliases
- `README.md` - High-level project description and development approach
- `.gitignore` - Includes generated JS files and cljs-runtime
- `alias-to-namespace` - Configuration map in translator.clj for namespace alias mappings

## Dependencies

### Core Dependencies
- `org.clojure/clojure` - 1.12.0-alpha5
- `rewrite-clj/rewrite-clj` - 1.1.47 (for AST manipulation)
- `borkdude/edamame` - 1.4.25 (for parsing reader conditionals and function literals)

### Development Aliases
- `:electric-dev` - Runs Electric app on port 8080
- `:reframe-dev` - Runs Re-frame app on port 8082
- `:nrepl` - REPL on port 7888

## Architecture

### Translation Flow
1. **Input**: Electric code from files or direct forms
2. **Processing**: Analyzes Electric forms using rewrite-clj with dependency resolution
3. **Output**: Map with three keys:
   - `:views` - Vector of canonical AST maps with metadata
   - `:events` - Vector of event handler forms (currently empty)
   - `:subs` - Vector of subscription forms (currently empty)
4. **File Writing**: Optional - writes forms to .cljs files with topological sorting

### Current Translation Patterns

#### Basic DOM Elements
```clojure
;; Electric
(dom/h1 (dom/text "Hello"))
(dom/p (dom/props {:class "text"}) (dom/text "Text"))

;; Re-frame
[:h1 "Hello"]
[:p {:class "text"} "Text"]
```

#### Component Calls
```clojure
;; Electric
(LabelAndAmount 0 0 "text" 20)

;; Re-frame
[label-and-amount-view 0 0 "text" 20]
```

#### e/defn Functions
```clojure
;; Electric
(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (dom/div ...))))

;; Re-frame
(defn main-view [ring-request]
  [:div ...])
```

#### Let Bindings & Control Flow
```clojure
;; Electric
(let [error false
      paths nil]
  (dom/div
    (if error
      (dom/div (dom/text error))
      (dom/div ...))))

;; Re-frame
(let [error false
      paths nil]
  [:div
   (if error
     [:div error]
     [:div ...])])
```

#### Event Handlers
```clojure
;; Electric
(dom/button
  (dom/On "click" #(wc-state/wc-mutation r-muts/take-out [:qr-code]) nil))

;; Re-frame
[:button
 {:on-click (fn [] (dispatch [:restaurant.events/take-out [:qr-code]]))}]
```

## Implementation Details

### Configuration

#### `alias-to-namespace` Map
Maps namespace aliases to full namespaces for event translation:
```clojure
(def ^:private alias-to-namespace
  {'r-events 'restaurant.events
   ;; Add more mappings as needed
   })
```
The translator will throw an exception if an unknown alias is encountered.

### Core Functions

#### `translate [forms-or-code] [starting-fn-name] [output-ns]`
Main entry point that accepts:
- A vector of forms from `read-file-forms`
- An e/defn form
- Single or multiple DOM forms

Returns canonical AST structure with `:views`, `:events`, and `:subs`.

#### `read-file-forms [file-path starting-fn-name]`
- Reads Electric code from files
- Extracts only the starting function and its dependencies
- Returns forms in topological order
- Uses edamame with `:fn true` for proper parsing

#### `write-forms-to-file! [ns-name ast-forms]`
- Writes forms to files with proper formatting
- Generates namespace declarations with requires
- Handles hyphenated → underscored directory conversion
- Uses zprint or pprint for formatting

### Translation Features

#### DOM Element Translation
- Handles `dom/text`, `dom/props`, and `dom/On`
- Preserves structure of let bindings and if expressions
- Supports nested elements and multiple children
- Wraps multiple root elements in React Fragment `[:<>]`

#### Event Handler Translation
- Converts `(dom/On "click" handler)` to `:on-click` props
- Transforms Electric mutations to Re-frame dispatches:
  - `wc-state/wc-mutation r-muts/take-out` → `dispatch [:restaurant.events/take-out]`
- Uses `alias-to-namespace` configuration for namespace resolution
- Handles both `fn` and `fn*` forms

#### Component Translation
- CamelCase → kebab-case-view conversion
- Preserves all arguments
- Maintains component hierarchy

#### Dependency Analysis
- Tracks function calls and symbol references
- Excludes framework functions (dom/*, e/*, svg/*)
- Builds dependency graph for topological sorting
- Handles let bindings and local scopes

### Testing Strategy

#### Test Organization
- All tests use hardcoded forms (never read from files)
- Tests are self-contained and reproducible
- 9 tests covering all current functionality
- Only one test with 'current' in name writes output files

#### Current Test Coverage
1. Direct DOM translation
2. Empty functions
3. Single elements
4. Nested elements
5. Component calls and dependencies
6. File output with topological sorting
7. Let bindings in e/defn forms
8. Let bindings with if expressions
9. Event handlers with mutations

### Development Workflow
1. Human updates Electric source and expected Re-frame output
2. AI creates new test with 'current' in name
3. **AI MUST create backups before modifying files** (save working versions to prevent corruption)
   - Use `cp src/translator/translator.clj src/translator/translator.clj.backup` before changes
   - Or commit to git before making changes
4. AI implements translation for new patterns
5. All tests must pass before proceeding
6. Manual verification of generated output

### Critical Safety Practices
- **Always backup files before modification** - Working code can be corrupted during edits
- Use version control (git) to save known-good states
- Test incrementally after each change
- If tests fail after changes, consider reverting to the backup
- Never trust that edits will preserve working functionality

## Current Status
✅ Complete:
- Basic DOM element translation
- Component call translation
- Let bindings and control flow
- Event handler translation with namespace resolution
- Dependency analysis and topological sorting
- File reading and writing
- 9 comprehensive tests

🚧 Next Steps:
- SVG element support
- Re-frame subscriptions
- More complex event patterns
- Error handling improvements

## Running the Project

### Tests
```bash
# Run all tests
clojure -Sdeps '{:paths ["src"]}' -M -e "(require '[translator.translation-test]) (clojure.test/run-tests 'translator.translation-test)"
```

### REPL Usage
```clojure
;; Read and translate from file
(translate (read-file-forms "electric-src/electric_starter_app/main.cljc" "Main") "Main" "reframe-output")

;; Translate direct forms
(translate '(dom/div (dom/text "Hello")))
```

### Manual Testing
1. Run Electric app: `clojure -M:electric-dev`
2. Run Re-frame app: `clojure -M:reframe-dev`
3. Compare behavior at localhost:8080 vs localhost:8082

### Coding Preferences
- **Default to public functions** - Use `defn` not `defn-`
- **Public defs** - Use `def` not `def ^:private`
- Private definitions should only be used when there's a specific need
- This is an internal tool, not a public API, so encapsulation is not a priority
- Testing and REPL exploration should be frictionless
- **No sed/regex for code changes** - Use structure-aware tools like rewrite-clj or manual edits
- Automatic code transformation should understand Clojure forms, not just text
