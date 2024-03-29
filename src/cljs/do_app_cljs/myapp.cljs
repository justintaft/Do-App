; A task can be a single item, or multiple items strung together.
; { title: string,
;   tags: [string],
;   due-date: date (optional)
;   sub-item-id: [int]
;   completed: bool
;   collapsed: bool
;  }

(ns do-app-cljs.myapp
  (:require [reagent.core :as r]))

(defonce todos (r/atom (sorted-map)))

(defonce counter (r/atom 0))

(defn add-todo
  "Add todo item. id is obtained by incremental global counter value."
  ([text] (add-todo nil text))
  ([parent-item text] (let [new-item-id (str "item-" (swap! counter inc))
                            new-item {:id new-item-id :title text :collapsed false :done false :sub-items [] :is-parent (nil? parent-item)}]
                        (swap! todos assoc new-item-id new-item)
                        (when parent-item (swap! todos (fn [todos child]
                                                          (update-in todos [(:id parent-item) :sub-items] #(conj % new-item-id))))) 
                        new-item)))
                       

                                               

(defn collapse [id] (swap! todos update-in [id :collapsed] not))
(defn toggle [id] (swap! todos update-in [id :done] not))
(defn save [id title] (swap! todos assoc-in [id :title] title))
(defn delete [id] (swap! todos dissoc id))

(defn mmap [m f a] (->> m (f a) (into (empty m))))
(defn complete-all [v] (swap! todos mmap map #(assoc-in % [1 :done] v)))
(defn clear-done [] (swap! todos mmap remove #(get-in % [1 :done])))


(defonce init (do
                (let [root (add-todo "Root")]
                  (let [work (add-todo root "Work")]
                    (add-todo work "Update Website")
                    (add-todo work "Another thing")
                    (let [provision (add-todo work "Provision Computer")]
                      (add-todo provision "Setup Debian")))
                  (let [home (add-todo root "Home")]
                    (add-todo home "Clean Room"))
                  (let [relationship (add-todo root "Relationship")]
                     (add-todo relationship "Birthday"))
                  (complete-all true))))

(defn todo-input [{:keys [title on-save on-stop]}]
  (let [val (r/atom title)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (if-not (empty? v) (on-save v))
                (stop))]
    (fn [{:keys [id class placeholder]}]
      [:input {:type "text" :value @val
               :id id :class class :placeholder placeholder
               :on-blur save
               :on-change #(reset! val (-> % .-target .-value))
               :on-key-down #(case (.-which %)
                               13 (save)
                               27 (stop)
                               nil)}])))




(def todo-edit (with-meta todo-input
                 {:component-did-mount #(.focus (r/dom-node %))}))

(defn todo-stats [{:keys [filt active done]}]
  (let [props-for (fn [name]
                    {:class (if (= name @filt) "selected")
                     :on-click #(reset! filt name)})]
    [:div
     [:span#todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :done) "Completed"]]]
     (when (pos? done)
       [:button#clear-completed {:on-click clear-done}
        "Clear completed " done])]))


(declare todo-items)

(defn item->id [item]
  (cond (map? item) (:id item)
        (instance? js/Object item) (if (.-getAttribute item)
                                     (.getAttribute item "id")
                                     (.attr (or (.-draggable item) item) "id"))))

;(defn item-change-parent [state item new-parent]
;  (let [item (if (string? item))]))
   
  
(defn handle-item-dropped! [event ui]
  ;;TODO prevent root parent item from being added to another list!
  ;;We could get duplicate items otherwise.
  (this-as this
    (let [item-id (item->id ui)]
      (swap! todos (fn [cur-todos]
                     ;; TODO This vals call will be very expensive if the list is big!
                     ;; TODO handle when dragging items between sibligns, parent items
                     ;; may not be defined always.
                     (let [old-parent-item (some (fn [item]
                                                   (if (some #{item-id} (:sub-items item))
                                                     item
                                                     nil))
                                                 (vals cur-todos))
                           new-parent-id (item->id this)]
                       (-> cur-todos
                           ;;Remove sub-item from old parent
                           (update-in [(item->id old-parent-item) :sub-items] (partial remove #(= item-id %))) 
                           ;;Add item to new parent
                           (update-in [new-parent-id :sub-items] #(conj % item-id)))))))))







                   
                                  
                   
(defn todo-component-mounted-or-updated [comp]
      (.draggable (js/jQuery (r/dom-node comp)))

      ;Greedy must be set to true to prevent drop event from firing multiple times
      ;for nested elements.
      (.droppable (js/jQuery (r/dom-node comp)) #js{:greedy true :drop handle-item-dropped!}))

(defn todo-item []
 "Retrufn function to render todo item"
  (r/create-class
   {:component-did-mount todo-component-mounted-or-updated
    :component-did-update todo-component-mounted-or-updated
    :reagent-render
     (fn []
       (let [editing (r/atom false)]
         (fn [{:keys [id done title sub-items collapsed]} items]
           [:li.todo-item {:id id :class (str (if done "completed ") (if @editing "editing"))}
            [:div.view
             [:input.toggle {:type "checkbox" :collapsed done
                             :on-change #(collapse id)}]
             [:input.toggle {:type "checkbox" :checked done
                             :on-change #(toggle id)}]
             [:label {:on-double-click #(reset! editing true)} title]
             [:button.destroy {:on-click #(delete id)}]]
            (when @editing
              [todo-edit {:class "edit" :title title
                          :on-save #(save id %)
                          :on-stop #(reset! editing false)}])
            (when (not (empty? sub-items))
                [:ul {:class (str (if collapsed "collapsed"))} [todo-items (map #(items %) sub-items) items]])])))}))

         

(defn todo-items [] 
    "Return function to render list of todo items"
    (fn [items-to-display all-items]
        [:div#items 
            (for [todo items-to-display] ^{:key (:id todo)} [todo-item todo all-items])]))


(defn todo-app [props]
  (let [filt (r/atom :all)]
    (fn []
      (let [all-items @todos
            items (vals all-items)
            done (->> items (filter :done) count)
            parent-items (filter :is-parent items)
            active (- (count items) done)]
        [:div
         [:section#todoapp
          [:header#header
           [:h1 "todos"]
           [todo-input {:id "new-todo"
                        :placeholder "What needs to be done?"
                        :on-save add-todo}]]
          (when (-> items count pos?)
            [:div
             [:section#main
              [:input#toggle-all {:type "checkbox" :checked (zero? active)
                                  :on-change #(complete-all (pos? active))}]
              [:label {:for "toggle-all"} "Mark all as complete"]
              [:ul#todo-list [todo-items parent-items all-items]]]

             [:footer#footer
              [todo-stats {:active active :done done :filt filt}]]])]
         [:footer#info
          [:p "Double-click to edit a todo"]]]))))

;(defn ^:export run []
;  (r/render [todo-app]
;            (js/document.getElementById "app")))


