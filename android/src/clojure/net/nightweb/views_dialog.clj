(ns net.nightweb.views-dialog
  (:require [neko.resource :as r]
            [neko.threading :as thread]
            [neko.ui :as ui]
            [net.clandroid.activity :as activity]
            [net.nightweb.actions :as actions]
            [net.nightweb.utils :as utils]
            [nightweb.constants :as c]
            [nightweb.db :as db]
            [nightweb.formats :as f]))

(defn get-new-post-view
  [context content]
  (let [post (:post content)
        page-content (activity/get-state context :share)
        pointers (if (empty? post)
                   {:ptrhash (when (and (:userhash page-content)
                                        (or (= :post (:type page-content))
                                            (-> (:userhash page-content)
                                                c/is-me?
                                                not)))
                               (:userhash page-content))
                    :ptrtime (when (= :post (:type page-content))
                               (:time page-content))}
                   {:ptrhash (:ptrhash post)
                    :ptrtime (:ptrtime post)})
        view (ui/make-ui
               context
               [:linear-layout {:orientation 1}
                [:linear-layout {:orientation 0
                                 :tag "user-info"}
                 [:image-view {:background-resource
                               (r/get-resource :drawable :profile)
                               :tag "user-img"}]
                 [:text-view {:single-line true
                              :tag "user-name"}]]
                [:edit-text {:min-lines 10
                             :tag "post-body"}]])
        user-info (.findViewWithTag view "user-info")
        user-img (.findViewWithTag view "user-img")
        user-name (.findViewWithTag view "user-name")
        post-body (.findViewWithTag view "post-body")
        pad (utils/make-dip context 10)
        s 80]
    (.setTag view pointers)
    (utils/set-text-size user-name utils/default-text-size)
    (.setMinHeight user-name s)
    (.setGravity user-name android.view.Gravity/CENTER_VERTICAL)
    (.setPadding user-name pad 0 0 0)
    (.setLayoutParams user-img (android.widget.LinearLayout$LayoutParams. s s))
    (.setScaleType user-img android.widget.ImageView$ScaleType/CENTER_CROP)
    (if-let [ptr-hash (:ptrhash pointers)]
      (future (let [user (db/get-single-user-data {:userhash ptr-hash})
                    path (utils/get-pic-path (:userhash user) (:pichash user))
                    bitmap (utils/path-to-bitmap path utils/thumb-size)]
                (thread/on-ui (.setText user-name (:title user))
                              (.setImageBitmap user-img bitmap))))
      (.setVisibility user-info android.view.View/GONE))
    (utils/set-text-size post-body utils/default-text-size)
    (utils/set-text-max-length post-body db/max-length-large)
    (.setText post-body (:body post))
    (actions/clear-attachments context)
    view))

(defn get-profile-view
  [context content]
  (let [user (:user content)
        bold android.graphics.Typeface/DEFAULT_BOLD
        view (if (c/is-me? (:userhash user))
               (ui/make-ui context
                           [:scroll-view {}
                            [:linear-layout {:orientation 1}
                             [:edit-text {:single-line true
                                          :layout-width :fill
                                          :tag "profile-title"}]
                             [:edit-text {:layout-width :fill
                                          :tag "profile-body"}]
                             [:relative-layout {}]]])
               (ui/make-ui context
                           [:scroll-view {}
                            [:linear-layout {:orientation 1}
                             [:text-view {:single-line true
                                          :layout-width :fill
                                          :text-is-selectable true
                                          :typeface bold}]
                             [:text-view {:layout-width :fill
                                          :text-is-selectable true}]
                             [:relative-layout {}]]]))
        linear-layout (.getChildAt view 0)
        text-name (.getChildAt linear-layout 0)
        text-body (.getChildAt linear-layout 1)
        relative-layout (.getChildAt linear-layout 2)
        image-view (proxy [android.widget.ImageButton] [context]
                     (onMeasure [width height]
                       (proxy-super onMeasure width width)))
        clear-btn (android.widget.Button. context)
        pad (utils/make-dip context 10)]
    ; set padding and text size
    (.setPadding linear-layout pad pad pad pad)
    (utils/set-text-size text-name utils/default-text-size)
    (utils/set-text-size text-body utils/default-text-size)
    (utils/set-text-max-length text-name db/max-length-small)
    (utils/set-text-max-length text-body db/max-length-large)
    ; set text content
    (when (c/is-me? (:userhash user))
      (.setHint text-name (r/get-string :name))
      (.setHint text-body (r/get-string :about_me)))
    (.setText text-name (:title user))
    (if (c/is-me? (:userhash user))
      (.setText text-body (:body user))
      (->> (:body user)
           (f/tags-encode :user)
           (utils/set-text-content context text-body actions/show-basic)))
    (.setText clear-btn (r/get-string :clear))
    ; set layout params for image view and clear button
    (let [fill android.widget.RelativeLayout$LayoutParams/FILL_PARENT
          params (android.widget.RelativeLayout$LayoutParams. fill 0)]
      (.setLayoutParams image-view params))
    (let [wrap android.widget.RelativeLayout$LayoutParams/WRAP_CONTENT
          params (android.widget.RelativeLayout$LayoutParams. wrap wrap)]
      (.addRule params android.widget.RelativeLayout/ALIGN_PARENT_TOP)
      (.addRule params android.widget.RelativeLayout/ALIGN_PARENT_RIGHT)
      (.setLayoutParams clear-btn params))
    ; set image view and clear button parameters
    (.setTag image-view "profile-image")
    (.setScaleType image-view android.widget.ImageView$ScaleType/CENTER_CROP)
    (.setBackgroundResource image-view (r/get-resource :drawable :profile))
    (future
      (let [bitmap (-> (utils/get-pic-path (:userhash user) (:pichash user))
                       (utils/path-to-bitmap utils/full-size))]
        (thread/on-ui (.setImageBitmap image-view bitmap))))
    (.addView relative-layout image-view)
    (when (c/is-me? (:userhash user))
      (.setOnClickListener
        image-view
        (proxy [android.view.View$OnClickListener] []
          (onClick [v]
            (actions/request-files
              context
              "image/*"
              (fn [uri]
                (let [pic (utils/uri-to-bitmap context (.toString uri))]
                  (.setImageBitmap image-view pic)))))))
      (.setOnClickListener clear-btn
                           (proxy [android.view.View$OnClickListener] []
                             (onClick [v]
                               (.setImageBitmap image-view nil))))
      (.addView relative-layout clear-btn))
    ; return the parent view
    view))

(defn get-welcome-view
  [context]
  (let [view (ui/make-ui
               context
               [:linear-layout {:orientation 1}
                [:text-view {:text (r/get-string :welcome_title)
                             :tag "title-text"
                             :layout-width :fill}]
                [:text-view {:text (r/get-string :welcome_subtitle)
                             :tag "subtitle-text"}]
                [:linear-layout {:orientation 0}
                 [:image-view {:image-resource
                               (r/get-resource :drawable :profile_small)
                               :tag "profile-image"}]
                 [:text-view {:text (r/get-string :welcome_profile)
                              :tag "profile-text"}]]
                [:linear-layout {:orientation 0}
                 [:image-view {:image-resource
                               (r/get-resource :drawable
                                             :android/ic_menu_add)
                               :tag "post-image"}]
                 [:text-view {:text (r/get-string :welcome_post)
                              :tag "post-text"}]]
                [:linear-layout {:orientation 0}
                 [:image-view {:image-resource
                               (r/get-resource :drawable
                                             :android/ic_menu_share)
                               :tag "share-image"}]
                 [:text-view {:text (r/get-string :welcome_share)
                              :tag "share-text"}]]])
        title-text (.findViewWithTag view "title-text")
        subtitle-text (.findViewWithTag view "subtitle-text")
        profile-text (.findViewWithTag view "profile-text")
        post-text (.findViewWithTag view "post-text")
        share-text (.findViewWithTag view "share-text")
        profile-image (.findViewWithTag view "profile-image")
        post-image (.findViewWithTag view "post-image")
        share-image (.findViewWithTag view "share-image")
        pad (utils/make-dip context 10)
        s 80]
    (utils/set-text-size title-text utils/large-text-size)
    (utils/set-text-size subtitle-text utils/default-text-size)
    (.setGravity title-text android.view.Gravity/CENTER_HORIZONTAL)
    (doseq [txt [profile-text post-text share-text]]
      (utils/set-text-size txt utils/default-text-size)
      (.setMinHeight txt s)
      (.setGravity txt android.view.Gravity/CENTER_VERTICAL))
    (doseq [img [profile-image post-image share-image]]
      (.setLayoutParams img (android.widget.LinearLayout$LayoutParams. s s)))
    (.setPadding view pad pad pad pad)
    view))
