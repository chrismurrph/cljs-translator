# PROJECT_SUMMARY.md

## Overview
This project is an AI-assisted tool for translating Electric Clojure programs into Re-frame applications. The translation converts Electric's reactive DOM API into Re-frame's Hiccup-based view syntax, along with any necessary events and subscriptions.

## Key File Paths

### Source Files
- `electric-src/electric_starter_app/main.cljc` - Electric source code to be translated
- `reframe-examples/reframe_examples/views.cljs` - Expected Re-frame view output
- `reframe-examples/reframe_examples/events.cljs` - Expected Re-frame events output
- `reframe-examples/reframe_examples/subs.cljs` - Expected Re-frame subscriptions output
- `src/translator/translator.clj` - Main translation implementation (moved to subdirectory)
- `src/translator/translation_test.clj` - Test suite (moved to src/translator/)

### Output Files (Generated)
- `reframe-output/reframe_output/views.cljs` - Generated Re-frame views
- `reframe-output/reframe_output/events.cljs` - Generated Re-frame events
- `reframe-output/reframe_output/subs.cljs` - Generated Re-frame subscriptions

### Configuration
- `deps.edn` - Project dependencies and development aliases
- `README.md` - High-level project description and development approach
- `.gitignore` - Includes generated JS files and cljs-runtime

## Dependencies

### Core Dependencies
- `org.clojure/clojure` - 1.12.0-alpha5
- `rewrite-clj/rewrite-clj` - 1.1.47 (for AST manipulation)
- `clojure.pprint` - Used for formatting generated code
- `clojure.walk` - Used for dependency analysis in forms

### Development Aliases
- `:electric-dev` - Runs Electric app on port 8080
- `:reframe-dev` - Runs Re-frame app on port 8082
- `:nrepl` - REPL on port 7888

## Architecture

### Translation Flow
1. **Input**: Electric code from `electric-starter-app.main`
2. **Processing**: Analyzes Electric forms using rewrite-clj with dependency resolution
3. **Output**: Map with three keys:
   - `:views` - Vector of view functions (either simple forms or maps with metadata)
   - `:events` - Vector of event handler forms
   - `:subs` - Vector of subscription forms
4. **File Writing**: Optional side-effect writes forms to respective .cljs files with topological sorting

### Current Translation Patterns

#### Electric → Re-frame View Mappings
```clojure
;; Electric
(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (dom/h1 (dom/text "Hello"))
      (dom/p (dom/text "Text")))))

;; Re-frame
(defn main-view [ring-request]
  [:<>
   [:h1 "Hello"]
   [:p "Text"]])
```

#### Component Call Patterns
- `(LabelAndAmount 0 0 "text" 20)` → `[label-and-amount-view 0 0 "text" 20]`
- CamelCase component names → kebab-case-view

#### DOM Element Patterns
- `(dom/h1 (dom/text "..."))` → `[:h1 "..."]`
- `(dom/p (dom/text "...") child1 child2)` → `[:p "..." child1 child2]`
- `(dom/a (dom/props {:href "..."}) (dom/text "..."))` → `[:a {:href "..."} "..."]`
- Multiple root elements wrapped in React Fragment `[:<>]`
- Parameters from e/defn are preserved in the Re-frame function

#### e/client Block Patterns
The translator handles two types of e/client blocks:
1. `(e/client (binding [...] body...))` - with binding vector
2. `(e/client body...)` - direct body without binding

#### Let Binding Patterns
The translator preserves let bindings in e/defn forms:
```clojure
;; Electric
(e/defn LabelAndAmountNew [top left text-of-label amt]
  (let [span-style (generate-absolute-style top left)
        div-class (->class :gen/row-indent)]
    (dom/span ...)))

;; Re-frame
(defn label-and-amount-new-view [top left text-of-label amt]
  (let [span-style (generate-absolute-style top left)
        div-class (->class :gen/row-indent)]
    [:span ...]))
```

## Implementation Details

### Core Functions

#### `translate [electric-code] [output-ns]`
Main entry point that accepts either:
- A vector of forms from read-file-forms (already sorted topologically)
- An e/defn form: `(e/defn Name [...] ...)`
- A single DOM form: `(dom/div ...)`
- Multiple DOM forms: `((dom/h1 ...) (dom/p ...))`

Returns a map with :views, :events, and :subs keys.
- When `output-ns` is NOT provided: returns simple forms for backward compatibility
- When `output-ns` IS provided: returns maps with `:view`, `:name`, `:deps`, `:topo-sort` keys and writes files

```clojure
;; Translate e/defn form
(translate '(e/defn Main [ring-request] ...))
;; => {:views [(defn main-view [ring-request] ...)], :events [], :subs []}

;; Translate direct DOM forms
(translate '(dom/div (dom/text "Hello")))
;; => {:views [[:div "Hello"]], :events [], :subs []}

;; Translate with file output
(translate '((dom/h1 ...) (dom/p ...)) "reframe-output")
;; => {:views [{:view ... :name ... :deps #{} :topo-sort 1}], :events [], :subs []}
;; Also writes to reframe-output/reframe_output/*.cljs files
```

#### `read-file-forms [file-path starting-fn-name]`
Reads a file and extracts Electric forms starting from a specific function and its dependencies:
- Handles reader conditionals in .cljc files
- Returns forms in topological order
- Only includes the starting function and its transitive dependencies

#### `write-forms-to-file! [ns-name forms]`
Public function that writes forms to files with:
- Proper formatting using pprint and cljfmt
- Namespace declaration with requires (e.g., `[restaurant.ui :as r-ui]`)
- Topological sorting when forms have `:topo-sort` metadata
- Hyphenated directory → underscored subdirectory structure

### Helper Functions

#### Dependency Analysis
- `find-dependencies` - Extracts function calls AND symbol references from a form using clojure.walk
  - Tracks local bindings to avoid false dependencies
  - Excludes self-references
  - Includes bare symbol references (e.g., `customer-columns-xs`)
- `topological-sort` - Orders functions so dependencies come before dependents

#### Translation Helpers
- `component-call?` - Detects uppercase component calls
- `translate-component-call` - Converts component calls to Hiccup vectors
- `extract-function-params` - Gets parameters from e/defn forms
- `find-client-binding-body` - Handles both binding and non-binding e/client blocks
- `translate-dom-element` - Handles DOM elements and preserves let bindings

### Testing Strategy
- Each round adds new Electric→Re-frame examples
- Tests verify translation output matches expected forms
- All tests (old and new) must pass each round
- Only ONE test with 'current' in its name writes to output files
- Currently 8 tests passing covering:
  - Direct DOM translation
  - Empty functions
  - Single elements
  - Nested elements
  - Component calls and dependencies
  - File output with topological sorting
  - Let bindings in e/defn forms

### The 'Current' Test Pattern
The project uses a specific pattern for managing which test writes to the output files:

1. **Only one test with 'current' in its name** - This test is responsible for writing to the output files
2. **Tests that write output are placed at the bottom** of the test file
3. **Each development round**:
   - The previous test with 'current' in its name is renamed with a number suffix (e.g., `-1`, `-2`, etc.)
   - **IMPORTANT**: When renaming, the `output-ns` parameter must be REMOVED so the test no longer writes files
   - A new test is created with 'current' in the name for the new Electric patterns
   - This ensures only the latest translation is written to the output files
4. **Test parameter patterns**:
   - Tests with 'current' in name: Call `translate` WITH `output-ns` parameter → writes files
   - Tests with number suffixes (-1, -2, etc.): Call `translate` WITHOUT `output-ns` parameter → no file I/O
   - Example current test: `(translate forms "Main" "reframe-output")` - has output-ns
   - Example numbered test: `(translate dom-forms)` - no output-ns parameter
5. **Naming progression**:
   - Round 1: `test-current-translation-with-output` → `test-translation-with-output-1` (remove output-ns)
   - Round 2: `test-current-translation-with-output` → `test-translation-with-output-2` (remove output-ns)
   - Round 3: `test-current-translation-with-output` → `test-translation-with-output-3` (remove output-ns)
   - And so on...
6. **Benefits**:
   - Output files always show the most recent translation
   - All historical tests are preserved and continue to run
   - Easy to see the progression of features by number
   - Tests that write output are grouped together at the bottom
   - Clear separation between pure logic tests (numbered) and integration tests with file I/O (current)

Current test: `test-current-translation-with-output` - handles let bindings in e/defn forms
Previous tests:
- `test-translation-with-output-3` - e/defn without e/client wrapper (no file I/O)
- `test-translation-with-output-2` - component calls and dependencies (no file I/O)
- `test-translation-with-output-1` - basic DOM translation (no file I/O)

### Running Tests
```bash
# Run all tests
clojure -Sdeps '{:paths ["src"]}' -M -e "(require '[translator.translation-test]) (clojure.test/run-tests 'translator.translation-test)"

# Run only the current test (that writes output files)
clojure -Sdeps '{:paths ["src"]}' -M -e "(require '[translator.translation-test]) (clojure.test/test-var #'translator.translation-test/test-current-translation-with-output)"
```

### Development Workflow
1. Human updates `electric-starter-app.main` with new Electric code
2. Human updates `reframe-examples.*` with expected Re-frame output
3. AI renames the previous 'current' test to add next number suffix
4. AI creates new test with 'current' in name for new patterns
5. AI ensures translation function handles new patterns
6. All tests pass before moving to next round

### Code Organization
- Translation logic in pure functions (when output-ns not provided)
- Use rewrite-clj for parsing and AST navigation
- Separate concerns: parsing, transformation, code generation
- File I/O isolated to `write-forms-to-file!` function
- Pretty printing for readable output
- Tests can use direct DOM forms instead of full e/defn forms for clarity
- Namespace changed to `translator.translator` (from just `translator`)
- File reading separate from translation logic

## Implementation with rewrite-clj

### Key Improvements
1. **Better parsing** - Uses proper AST navigation instead of treating code as raw lists
2. **Preserves structure** - Can maintain formatting and comments
3. **More robust** - Handles edge cases better with zipper navigation
4. **Extensible** - Easy to add more complex transformations using zipper operations
5. **Dependency tracking** - Analyzes function calls to build dependency graphs
6. **Let binding support** - Preserves let bindings in translated functions

### How rewrite-clj is used
1. **Creating zippers**: `z/of-string` or `z/of-file` to parse code
2. **Navigation**: `z/find-value`, `z/next`, `z/down`, `z/right` to move through AST
3. **Inspection**: `z/sexpr` to get values, `z/list?` to check node types
4. **Finding patterns**: Custom predicates in `z/find` to locate specific forms
5. **Building results**: Collecting nodes and transforming them into Re-frame syntax

## File Output Structure

The translator writes to a specific directory structure:
- Base directory: `reframe-output/` (with hyphen)
- Subdirectory: `reframe_output/` (with underscore)
- Files: `views.cljs`, `events.cljs`, `subs.cljs`

Generated files include:
- Namespace declaration with requires
- Functions in topological order (dependencies first)
- Blank line before each function
- Pretty-printed Clojure forms for readability using cljfmt

## Advanced Features

### Topological Sorting
Views are automatically sorted so that:
- Helper functions appear before functions that use them
- No "undefined variable" errors in generated code
- Clean dependency order for readability

### View Metadata
When writing files, views include metadata:
- `:view` - The actual function form
- `:name` - The function name (symbol)
- `:deps` - Set of function names this view depends on
- `:topo-sort` - Number indicating position in dependency order

### Dependency Analysis Improvements
The `find-dependencies` function now:
- Tracks both function calls AND bare symbol references
- Correctly identifies `def` forms as dependencies (e.g., `customer-columns-xs`)
- Excludes local bindings and function parameters
- Prevents self-references
- Handles both `(function-call ...)` and bare `symbol-reference` patterns

## Extension Points

### Future Translation Targets
- Electric reactive bindings → Re-frame subscriptions
- Electric event handlers → Re-frame events
- Electric state management → Re-frame app-db
- Fulcro programs (mentioned as future input format)
- SVG elements (currently in PaidLabel, not yet translated)

### Potential Enhancements
- Support for Electric's server-side code
- Handling of Electric's reactive primitives
- Translation of more complex UI patterns
- Error handling and validation
- Preserving comments from Electric source

## Tools and APIs

### Available Tools (via Clojure MCP)
- `read_file` - Read source files
- `file_write` - Write translated output
- `clojure_edit` - Structured editing of Clojure forms
- `clojure_eval` - Test code in REPL
- `grep`/`glob_files` - Find patterns in codebase
- `bash` - Run shell commands for testing

### rewrite-clj Usage
- Parse Electric code into zipper/node structure
- Transform AST nodes based on patterns
- Generate formatted Clojure code
- Preserve code structure and formatting where appropriate

## Current Status
- Component call translation implemented (CamelCase → kebab-case-view)
- Parameter preservation from e/defn forms
- Helper function extraction (def, defn)
- Dependency analysis includes both function calls AND symbol references
- Topological sorting ensures correct definition order
- Test suite with 8 passing tests
- Let binding support in e/defn forms
- Clean separation between file reading and translation
- Project structure updated (translator namespace in subdirectory)
- Ready for next round: potentially event handlers, subscriptions, or SVG elements

## IMPORTANT NOTES FOR NEXT CHAT

### Current Status
The translator now successfully handles let bindings in e/defn forms:
- `test-current-translation-with-output` demonstrates translation of `LabelAndAmountNew` with let bindings
- The let binding structure is preserved in the output
- All 8 tests are passing

### Key Design Decision
The `translate` function does NOT handle file paths. It only accepts:
- A vector of forms from read-file-forms (already sorted topologically)
- An e/defn form: `(e/defn Name [...] ...)`
- A single DOM form: `(dom/div ...)`
- Multiple DOM forms: `((dom/h1 ...) (dom/p ...))`

File reading should be done by separate functions like `read-file-forms` or `read-file-forms`.

### Recent Accomplishments
1. **Let Binding Support**: The translator now handles e/defn forms with let bindings (no e/client wrapper)
2. **Clean Separation**: File reading is separate from translation
3. **Test Structure**: Current test follows the pattern of previous tests with hardcoded forms
4. **File Output**: Successfully writes formatted Re-frame code with proper namespace requires

### Next Steps
Potential areas for the next round:
- Handle more complex Electric patterns (SVG elements from PaidLabel?)
- Add support for Electric reactive bindings → Re-frame subscriptions
- Add support for Electric event handlers → Re-frame events
- Improve error handling and validation
