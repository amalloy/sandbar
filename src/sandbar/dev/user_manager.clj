;; Copyright (c) Brenton Ashworth. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns sandbar.dev.user-manager
  (:use (ring.util [response :only (redirect)])
        (compojure core)
        (sandbar core
                 stateful-session
                 [auth :only (hash-password)]
                 )
(sandbar.dev 				forms tables util
                 standard-pages validation
                 basic-authentication)))

(defn secure-user
  "Ensure that the user has a salt value associated with it and that if the
   password has changed, it is hashed."
  [new-user old-user]
  (let [password (:password new-user)
        salt (if-let [s (:salt old-user)]
               s
               (random-string 12 12))]
    (if (not (= (:password old-user) password))
      (-> new-user
         (assoc :salt salt)
         (assoc :password (hash-password password salt)))
      new-user)))

(def user-table-columns
     [{:column :last_name :actions #{:sort}}
      {:column :first_name :actions #{:sort}}
      {:column :username :actions #{:sort}}
      {:column :email :actions #{:sort}}
      :empty])

(defn user-list-page [props load-fn request]
  [:div
   [:div (clink-to "new" "Add new User")]
   (filter-and-sort-table
    (:params request)
     {:type :app_user :name :user-table :props props}
     user-table-columns 
     (fn [k row-data]
       (cond (= k :empty)
             [:div
              (clink-to (str "edit?id=" (:id row-data)) "Edit") ", "
              (clink-to (str "delete?id=" (:id row-data)) "Delete")]
             :else (k row-data)))
     load-fn)])

(defn user-form-fields [load-fn props]
  [(form-textfield props :username :required)
   (form-password props :new_password :required)
   (form-textfield props :first_name :required)
   (form-textfield props :last_name :required)
   (form-textfield props :email :required)
   (form-checkbox props :account_enabled)
   (form-multi-checkbox props :roles (load-fn :role) :name)])

(defn edit-user-form [data-fns props request]
  (let [lookup-fn (fn [r] ((data-fns :lookup) :app_user (get (:params r) "id")))
        action (if (.endsWith (:uri request) "new") :new :edit)
        form-data (if (= action :new) {} (lookup-fn request))
        title (if (= action :new) "Create New User" "Edit User")]
       (standard-form
        title (name action) "Save"
        (form-layout-grid [1 1 2 1 1 1]
                          :user
                          (conj
                           (user-form-fields (data-fns :load) props)
                           (form-hidden :id)
                           (form-hidden :password))
                          request
                          (if (= action :edit)
                            (assoc form-data :new_password "_unchanged")
                            form-data)))))

(defn create-user-from-params [load-fn params]
  (let [user (-> (get-params [:id :username :new_password :password
                              :first_name :last_name :email]
                             params)
                 (get-yes-no-fields params #{:account_enabled})
                 (get-multi-checkbox params :roles)
                 (assoc :type :app_user)
                 (clean-form-input))
        user (if (= "_unchanged" (:new_password user))
               user
               (assoc user :password (:new_password user)))]
    user))

(defn user-validator [props]
  (build-validator (non-empty-string :username
                                     :new_password
                                     :first_name
                                     :last_name
                                     :email props)))

(defn save-user! [data-fns props request]
  (redirect
   (let [params (:params request)
         success "list"]
     (if (form-cancelled? params)
       success
       (let [save-or-update-fn (data-fns :save)
             form-data (create-user-from-params (data-fns :load) params)
             failure (cpath (:uri request))]
         (if-valid (user-validator props) form-data
                   #(do
                      (save-or-update-fn (dissoc % :new_password))
                      (set-flash-value! :user-message
                                        "User has been saved.")
                      success)
                   (store-errors-and-redirect :user failure)))))))

;;
;; Functions for working with users
;; ================================
;;

(defn load-user-by [load-fn user k]
  (first
   (load-fn :app_user
            {k (user k)}
            {})))

(defn standard-save-user [user load-fn delete-fn save-fn]
  (let [all-roles (index-by :name (load-fn :role))
        new-roles (map #(all-roles %) (:roles user))
        new-role-ids (set (map :id new-roles))
        user-part (dissoc user :roles)
        current-roles (load-fn :user_role
                               {:user_id (:id user)}
                               {})
        current-role-ids (set (map :role_id current-roles))
        d-roles (filter #(not (contains? new-role-ids (:role_id %)))
                        current-roles)]
    (do
      (save-fn (secure-user user-part (load-user-by load-fn user :id)))
      (let [saved-user-id (:id (load-user-by load-fn user :username))
            a-roles (map #(hash-map :type :user_role
                                    :role_id (:id %)
                                    :user_id saved-user-id)
                         (filter #(not (contains? current-role-ids
                                                  (:id %)))
                                 new-roles))]
        (do
          (doseq [next d-roles]
            (delete-fn next))
          (doseq [next a-roles]
            (save-fn next)))))))

(defn standard-lookup-user [type id load-fn lookup-fn]
  (let [user (lookup-fn type id)
        roles (load-fn :role)
        user-role-ids
        (set (map :role_id
                  (load-fn :user_role
                           {:user_id id}
                           {})))
        user-roles (filter #(contains? user-role-ids (:id %)) roles)]
    (assoc user :roles (vec (map :name user-roles)))))

(defn standard-delete-user [type id load-fn delete-fn]
  (let [user (first (load-fn type {:id id} {}))
        roles (load-fn :user_role {:user_id (:id user)} {})]
    (doseq [next-role roles]
      (delete-fn :user_role (:id next-role)))
    (delete-fn type (:id user))))

(defn user-model [load-fn]
  {:load-login-user
   (fn [username password]
     (let [user (first (load-fn :app_user
                                {:username username} {}))
           roles (index-by :id (load-fn :role))]
       (-> {:username username :password password}
           (assoc :password-hash (:password user))
           (assoc :salt (:salt user))
           (assoc :roles (set
                          (map #(keyword (:name (roles %)))
                               (map :role_id
                                    (load-fn :user_role
                                             {:user_id (:id user)} {}))))))))
   :validate-password
   (fn [m]
     (if (and (:salt m)
              (:password-hash m)
              (= (hash-password (:password m) (:salt m))
                 (:password-hash m)))
       m
       (add-validation-error m "Incorrect username or password!")))})

;;
;; Routes
;; ======
;;

(defn security-edit-user-routes [path-prefix layout name-fn props data-fns]
  (routes
   (GET (str path-prefix "/user/list*") request
        (layout (name-fn request)
                request
                (user-list-page props (data-fns :load) request)))
   (GET (str path-prefix "/user/new*") request
        (layout (name-fn request)
                request
                (edit-user-form data-fns props request)))
   (POST (str path-prefix "/user/new*") request
         (save-user! data-fns props request))
   (GET (str path-prefix "/user/edit*") request
        (layout (name-fn request)
                request
                (edit-user-form data-fns props request)))
   (POST (str path-prefix "/user/edit*") request
         (save-user! data-fns props request))
   (GET (str path-prefix "/user/delete*") request
        (layout (name-fn request)
                request
                (confirm-delete (data-fns :lookup)
                                :app_user
                                (fn [u]
                                  (str (:first_name u) " " (:last_name u)))
                                props
                                (get (:params request) "id"))))
   (POST (str path-prefix "/user/delete*") {params :params}
         (do
           (if (not (form-cancelled? params))
             ((data-fns :delete) :app_user (get params "id")))
           (redirect "list")))))
