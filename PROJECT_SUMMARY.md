# PROJECT_SUMMARY.md

## Overview
This project is an AI-assisted tool for translating Electric Clojure programs into Re-frame applications. The translation converts Electric's reactive DOM API into Re-frame's Hiccup-based view syntax, along with any necessary events and subscriptions.

## Key File Paths

### Source Files
- `electric-src/electric_starter_app/main.cljc` - Electric source code to be translated
- `reframe-examples/reframe_examples/views.cljs` - Expected Re-frame view output
- `reframe-examples/reframe_examples/events.cljs` - Expected Re-frame events output
- `reframe-examples/reframe_examples/subs.cljs` - Expected Re-frame subscriptions output

### Configuration
- `deps.edn` - Project dependencies and development aliases
- `README.md` - High-level project description and development approach

### Test Directory
- `test/` - Directory for translation tests (to be created)

## Dependencies

### Core Dependencies
- `org.clojure/clojure` - 1.12.0-alpha5
- `rewrite-clj/rewrite-clj` - 1.1.47 (for AST manipulation)

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
4. **File Writing**: Impure wrapper writes forms to respective .cljs files

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

## Implementation Conventions

### Testing Strategy
- Each round adds new Electric→Re-frame examples
- Tests verify translation output matches expected forms
- All tests (old and new) must pass each round
- Tests use the pure translation function only

### Development Workflow
1. Human updates `electric-starter-app.main` with new Electric code
2. Human updates `reframe-examples.*` with expected Re-frame output
3. AI creates/updates tests for new patterns
4. AI ensures translation function handles new patterns
5. All tests pass before moving to next round

### Code Organization
- Translation logic should be pure functions
- Use rewrite-clj for parsing and potentially generating code
- Separate concerns: parsing, transformation, code generation
- File I/O kept in wrapper functions only

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
- Basic Electric DOM to Hiccup translation example provided
- Test framework to be implemented
- Translation function to be created
- Iterative development process defined
