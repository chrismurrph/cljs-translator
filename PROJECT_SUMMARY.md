# PROJECT_SUMMARY.md

## Overview
This project is an AI-assisted tool for translating Electric Clojure programs into Re-frame applications. The translation converts Electric's reactive DOM API into Re-frame's Hiccup-based view syntax, along with any necessary events and subscriptions.

## Key File Paths

### Source Files
- `electric-src/electric_starter_app/main.cljc` - Electric source code to be translated
- `reframe-examples/reframe_examples/views.cljs` - Expected Re-frame view output
- `reframe-examples/reframe_examples/events.cljs` - Expected Re-frame events output
- `reframe-examples/reframe_examples/subs.cljs` - Expected Re-frame subscriptions output
- `src/translator.clj` - Main translation implementation
- `test/translation_test.clj` - Test suite

### Output Files (Generated)
- `reframe-output/reframe_output/views.cljs` - Generated Re-frame views
- `reframe-output/reframe_output/events.cljs` - Generated Re-frame events
- `reframe-output/reframe_output/subs.cljs` - Generated Re-frame subscriptions

### Configuration
- `deps.edn` - Project dependencies and development aliases
- `README.md` - High-level project description and development approach
- `.gitignore` - Includes generated JS files and cljs-runtime

### Test Directory
- `test/` - Directory containing translation tests

## Dependencies

### Core Dependencies
- `org.clojure/clojure` - 1.12.0-alpha5
- `rewrite-clj/rewrite-clj` - 1.1.47 (for AST manipulation)
- `clojure.pprint` - Used for formatting generated code

### Development Aliases
- `:electric-dev` - Runs Electric app on port 8080
- `:reframe-dev` - Runs Re-frame app on port 8082
- `:nrepl` - REPL on port 7888

## Architecture

### Translation Flow
1. **Input**: Electric code from `electric-starter-app.main`
2. **Processing**: Pure function analyzes Electric forms using rewrite-clj
3. **Output**: Map with three keys:
   - `:views` - Vector of view function forms
   - `:events` - Vector of event handler forms
   - `:subs` - Vector of subscription forms
4. **File Writing**: Optional impure side-effect writes forms to respective .cljs files

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
(defn main-view []
  [:<>
   [:h1 "Hello"]
   [:p "Text"]])
```

#### DOM Element Patterns
- `(dom/h1 (dom/text "..."))` → `[:h1 "..."]`
- `(dom/p (dom/text "...") child1 child2)` → `[:p "..." child1 child2]`
- `(dom/a (dom/props {:href "..."}) (dom/text "..."))` → `[:a {:href "..."} "..."]`
- Multiple root elements wrapped in React Fragment `[:<>]`
- CamelCase Electric names → kebab-case-view Re-frame names

## Implementation Details

### Core Functions

#### `translate [electric-code] [output-ns]`
Main entry point that takes Electric code as a quoted form and returns a map with :views, :events, and :subs keys.
When `output-ns` is provided, also writes the generated forms to files.

```clojure
(translate '(e/defn Main [ring-request] ...))
;; => {:views [...], :events [], :subs []}

(translate '(e/defn Main [ring-request] ...) "reframe-output")
;; => {:views [...], :events [], :subs []}
;; Also writes to reframe-output/reframe_output/*.cljs files
```

#### `translate-file [file-path]`
Alternative entry point that works directly with .cljc files, finding all e/defn forms in the file.

#### `write-forms-to-file! [ns-name forms]`
Internal function that writes forms to files with proper formatting:
- Handles the hyphenated directory → underscored subdirectory structure
- Uses pprint for readable output
- Adds blank line before each function for proper spacing

### Testing Strategy
- Each round adds new Electric→Re-frame examples
- Tests verify translation output matches expected forms
- All tests (old and new) must pass each round
- Only ONE test with 'current' in its name writes to output files
- Currently 5 tests passing covering: basic translation, empty functions, single elements, nested elements, file output

### Running Tests
```bash
# Run all tests
clojure -Sdeps '{:paths ["src" "test"]}' -e "(require '[clojure.test]) (clojure.test/run-tests 'translation-test)"

# Run only the current test (that writes output files)
clojure -Sdeps '{:paths ["src" "test"]}' -e "(require '[clojure.test]) (clojure.test/test-var #'translation-test/test-current-translation-with-output)"
```

### Development Workflow
1. Human updates `electric-starter-app.main` with new Electric code
2. Human updates `reframe-examples.*` with expected Re-frame output
3. AI creates/updates tests for new patterns
4. AI ensures translation function handles new patterns
5. AI maintains ONE test with 'current' in name for file output
6. All tests pass before moving to next round

### Code Organization
- Translation logic in pure functions (when output-ns not provided)
- Use rewrite-clj for parsing and AST navigation
- Separate concerns: parsing, transformation, code generation
- File I/O isolated to `write-forms-to-file!` function
- Pretty printing for readable output

## Implementation with rewrite-clj

### Key Improvements
1. **Better parsing** - Uses proper AST navigation instead of treating code as raw lists
2. **Preserves structure** - Can maintain formatting and comments
3. **More robust** - Handles edge cases better with zipper navigation
4. **File support** - Added `translate-file` function that can work directly with .cljc files
5. **Extensible** - Easy to add more complex transformations using zipper operations

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
- Namespace declaration
- Blank line before each function
- Pretty-printed Clojure forms for readability

## Extension Points

### Future Translation Targets
- Electric reactive bindings → Re-frame subscriptions
- Electric event handlers → Re-frame events
- Electric state management → Re-frame app-db
- Fulcro programs (mentioned as future input format)

### Potential Enhancements
- Support for Electric's server-side code
- Handling of Electric's reactive primitives
- Translation of more complex UI patterns
- Error handling and validation
- Integration with cljfmt for even better formatting
- Preserving comments from Electric source

## Tools and APIs

### Available Tools (via Clojure MCP)
- `read_file` - Read source files
- `file_write` - Write translated output
- `clojure_edit` - Structured editing of Clojure forms
- `clojure_eval` - Test code in REPL
- `grep`/`glob_files` - Find patterns in codebase

### rewrite-clj Usage
- Parse Electric code into zipper/node structure
- Transform AST nodes based on patterns
- Generate formatted Clojure code
- Preserve code structure and formatting where appropriate

## Current Status
- Basic Electric DOM to Hiccup translation implemented
- Test suite with 5 passing tests (including file output test)
- Translation function using rewrite-clj for robust AST manipulation
- Optional file output with proper formatting
- Ready for iterative development of more complex patterns
- Project set up for GitHub repository (electric-to-reframe)
