(ns restaurant.with-customer.demo-data
  (:require
   [com.pangglow.util :as utl]
   [restaurant.domain :as r-domain]))

(defn example-bill-identifiers [ms]
  (let [mk-bill-identifier (partial r-domain/mk-bill-identifier ms :bill-identifier.origin/original)
        men ["James" "John" "Robert" "Michael" "William" "David" "Richard" "Joseph" "Charles" "Thomas" "Daniel" "Matthew" "Anthony" "Christopher" "Joshua" "Andrew" "Kevin" "Brian" "George" "Timothy"]
        women ["Mary" "Patricia" "Jennifer" "Linda" "Elizabeth" "Barbara" "Susan" "Jessica" "Sarah" "Karen" "Nancy" "Margaret" "Lisa" "Betty" "Dorothy" "Helen" "Sandra" "Ashley" "Kimberly" "Emily"]
        entities (->> (reduce into [(r-domain/gen-many-text-f "Room") (r-domain/gen-many-text-f "Table") (r-domain/gen-many-text-f "Stick") men women])
                   (sort)
                   (mapv mk-bill-identifier))]
    entities))

;;
;; Used in pos. is it?
;;
#_(defn example-menu [ms]
  (let [mk-heading (partial r-domain/mk-item ms)
        drinks-heading (mk-heading "Drinks")
        sodas-heading (r-domain/mk-middle-heading ms "Sodas" (:item/id drinks-heading))
        soda-drinks (let [{:item/keys [id]} sodas-heading
                          mk-drink (fn [[desc price]] (r-domain/mk-item ms desc price id))
                          items [["Coca Cola" 80]
                                 ["Sprite" 80]
                                 ["Water" 9]]]
                      (mapv mk-drink items))
        alcoholic-heading (r-domain/mk-middle-heading ms "Alcoholic Drinks" (:item/id drinks-heading))
        alcoholic-drinks (let [{:item/keys [id]} alcoholic-heading
                               mk-drink (fn [[desc price]] (r-domain/mk-item ms desc price id))
                               items [["Smirnoff Mule" 100]
                                      ["Margarita" 230]
                                      ["San Migel Light" 80]
                                      ["San Migel Pilsen" 70]
                                      ["Red Horse Stallion" 80]]]
                           (mapv mk-drink items))
        beef-dishes-heading (mk-heading "Beef Dishes")
        beef-dishes (let [{:item/keys [id]} beef-dishes-heading
                          mk-beef-dish (fn [[desc price]] (r-domain/mk-item ms desc price id))
                          items [["Beef Steak" 350]
                                 ["Beef with Broccoli" 380]]]
                      (mapv mk-beef-dish items))
        a-la-carte-heading (mk-heading "A La Carte")
        a-la-cartes (let [{:item/keys [id]} a-la-carte-heading
                          mk-a-la-carte (fn [[desc price]] (r-domain/mk-item ms desc price id))
                          items [["Plain Rice" 30]
                                 ["Garlic Rice" 40]
                                 ["Fried Egg" 25]
                                 ["Bacon Strips" 120]
                                 ["Bacon Bits" 120]
                                 ["Baked Beans" 60]
                                 ["Caramelized Onion" 60]
                                 ["Sourdough Slice" 40]
                                 ["Grilled Eggplant" 130]
                                 ["Spinach" 55]]]
                      (mapv mk-a-la-carte items))
        desserts-heading (mk-heading "Desserts")
        desserts (let [{:item/keys [id]} desserts-heading
                       mk-dessert (fn [[desc price]] (r-domain/mk-item ms desc price id))
                       items [["Choco Oat Cookies" 65]
                              ["Oat Cookies" 55]
                              ["Affogato" 188]
                              ["Vanilla Ice Cream" 119]
                              ["Frozen Leche Flan" 110]]]
                   (mapv mk-dessert items))
        salads-heading (mk-heading "Salads")
        salads (let [{:item/keys [id]} salads-heading
                     mk-salad (fn [[desc price]] (r-domain/mk-item ms desc price id))
                     items [["Falafel Salad" 420]
                            ["Acme Garden Salad" 235]
                            ["Bombay Carrot Salad" 240]]]
                 (mapv mk-salad items))
        sandwiches-heading (mk-heading "Sandwiches, Pita & Burgers")
        sandwiches (let [{:item/keys [id]} sandwiches-heading
                         mk-sandwich (fn [[desc price]] (r-domain/mk-item ms desc price id))
                         items [["Acme Sandwich" 325]
                                ["Acme Beef Burger" 395]
                                ["Falafel (Burger)" 365]
                                ["Falafel (Falapita)" 365]
                                ["Chicken Pita Pocket" 285]
                                ["Burrito Banger (Chicken)" 295]
                                ["Burrito Banger (Tofu)" 385]]]
                     (mapv mk-sandwich items))
        items (reduce into [[drinks-heading] [sodas-heading] [alcoholic-heading] [beef-dishes-heading] [a-la-carte-heading] [desserts-heading]
                            [salads-heading] [sandwiches-heading]
                            soda-drinks alcoholic-drinks beef-dishes a-la-cartes desserts salads sandwiches])]
    (utl/assrt (every? utl/entity? items) ["Some items not entities" {:example (some (fn [item]
                                                                                       (when-not (utl/entity? item)
                                                                                         item))
                                                                                 items)}])
    items))

(def example-items ["Bacon Bits" "Bacon Strips" "Burrito Banger (Tofu)" "Chicken Pita Pocket" "Vanilla Ice Cream"
                    "Bombay Carrot Salad" "Burrito Banger (Chicken)" "Falafel Salad" "Choco Oat Cookies" "Acme Sandwich"
                    "Coca Cola"])

#_(comment
  (take 10 (example-menu 1)))
