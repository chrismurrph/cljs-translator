(ns reframe-output.views
  (:require [js-interop :as js-interop]
            [r-muts :as r-muts]
            [utl :as utl]))

(defn ->class [_] "")

(def debug? true)

(defn login-screen
  [mut-f {:keys [username password organisation]} org?]
  (let [get-values (fn []
                     (let [un-value (js-interop/get-value username)
                           pw-value (js-interop/get-value password)]
                       (cond-> [un-value pw-value]
                         org? (conj (js-interop/get-value organisation)))))
        valid? (fn [values] (every? utl/not-blank? values))
        inject
        (fn [[un-value pw-value org-value :as values]]
          (utl/nothing debug?
                       "Sign In!"
                       {:organisation org-value, :username un-value})
          (mut-f r-muts/inject-many [:username :password :organisation] values))
        keydown (fn [e]
                  (let [key-pressed (.-key e)]
                    (when (= "Enter" key-pressed)
                      (let [values (get-values)]
                        (when (valid? values) (inject values))))))
        reason "Focusing on username"]
    (utl/nothing debug? reason)
    (js-interop/focus-on-dom-id-3 username)
    [:div {:class (->class :login/body)}
     [:div {:class (->class :login/login-box)}
      [:h1 {:class (->class :login/h1)} "Register/Login"]
      [:div
       [:div {:class (->class :login/input-group)}
        [:label {:for username, :class (->class :login/label)} "Username"]
        [:input
         {:name "username",
          :on-keydown keydown,
          :type "text",
          :id username,
          :class (->class :login/input)}]]
       [:div {:class (->class :login/input-group)}
        [:label {:for password, :class (->class :login/label)} "Password"]
        [:input
         {:name "password",
          :on-keydown keydown,
          :type "password",
          :id password,
          :class (->class :login/input)}]]
       (when org?
         [:div {:class (->class :login/input-group)}
          [:label {:for organisation, :class (->class :login/label)}
           "Organisation"]
          [:input
           {:name "organisation",
            :on-keydown keydown,
            :type "text",
            :id organisation,
            :class (->class :login/input)}]])
       [:button
        {:on-click (fn* [] (let [values (get-values)] (inject values))),
         :class (->class :login/button)} "Sign In"]]]]))

(defn main-view
  [ring-req]
  (let [f (constantly true)
        m {:organisation "Edgewood", :password "pass", :username "Chris"}]
    [login-screen f m false]))