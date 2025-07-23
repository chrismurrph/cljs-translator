# Trialling AI App

This project exists for AI (Claude via Clojure MCP) to create a program that can translate parts of an Electric program into a Re-frame one.
The intention is that later another entry point is able to accept a Fulcro program as input. The parts in the output can be divided up into
three forms: views, events, subs. Thus the expected output of this translation program is functions in three files:
views.cljs, events.cljs and subs.cljs

For manual (human) whole of application testing there are two aliases in deps.edn:

* :electric-dev 9630 8080 /     Electric
* :reframe-dev  9630 8082 /demo Re-frame

These should be of little concern to the AI, which only needs to work at the Clojure level, translating forms.

The tests that the AI is to generate use the fact that the translation output (various of the 3 forms) is already given at the ns `reframe-examples`.
rewrite-clj ought to be used to do the translation. Hence it is in deps.edn. The AI should advise if other deps are needed. Perhaps some of
the other deps that Clojure-MCP uses need to be added (cljfmt, clj-kondo, parinfer)? The job of the AI program is to write a pure function
that analyses the Electric code and returns a map with three keys: :views, :events, :subs. Each key has a vector of forms. An impure
wrapper function can write out the actual functions in the three files: views.cljs, events.cljs and subs.cljs. The tests can just test the
pure function - each test will do the same thing - call the pure function - and then examine aspects of the output. I will manually look at
the functions from the three .cljs files. To reiterate - after the tests are passing the only other thing for the AI to do is write to the
three .cljs files.

Development will progress in rounds. In each round I will replace what is at the namespaces `electric-starter-app.main` and `reframe-examples.*` after
checking that the previous round put all these from->to forms in tests. i.e. the code from namespaces `electric-starter-app.main` and `reframe-examples.*` is being kept. Each round will create more tests. During each round of course all tests must pass, not just the ones created that round. That's how together AI and human (me) can build a translation program.
