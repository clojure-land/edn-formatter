
(ns app.comp.container
  (:require [cljs.reader :refer [read-string]]
            [hsl.core :refer [hsl]]
            [respo-ui.core :as ui]
            [respo.core
             :refer
             [defcomp >> list-> <> div button span textarea pre a defeffect]]
            [respo.comp.space :refer [=<]]
            [respo.comp.inspect :refer [comp-inspect]]
            [reel.comp.reel :refer [comp-reel]]
            [fipp.edn :refer [pprint]]
            [favored-edn.core :refer [write-edn]]
            ["copy-text-to-clipboard" :as copy!]
            [app.config :as config]
            [cirru-edn.core :as cirru-edn]
            ["@mvc-works/codearea" :refer [codearea]]
            ["cson-parser/lib/stringify" :as cson-stringify]
            [respo-ui.comp :refer [comp-link comp-button]]
            [respo-alerts.core :refer [use-prompt]]
            [cljs.reader :refer [read-string]]
            [clojure.string :as string]))

(defcomp
 comp-input-area
 (text)
 (textarea
  {:value text,
   :autofocus true,
   :placeholder "Paste EDN here, press Command Enter",
   :style (merge
           ui/textarea
           ui/flex
           {:font-family ui/font-code, :font-size 12, :word-break :break-all}),
   :on-input (fn [e d! m!] (d! :text (:value e)))}))

(defcomp
 comp-drafter
 (states store)
 (div
  {:style (merge ui/flex ui/column)}
  (div
   {:style ui/row-parted}
   (span {})
   (div
    {:style (merge ui/row-middle {:padding 8})}
    (<> span (:error store) {:color :red, :margin-right 8})
    (comp-button
     {:text "Read EDN",
      :type :main,
      :on-click (fn [e d!]
        (try
         (let [data (read-string (:text store))] (d! :data {:data data, :error nil}))
         (catch js/Error err (d! :data {:data nil, :error (.-message err)}))))})
    (=< 8 nil)
    (comp-button
     {:text "Read JSON",
      :on-click (fn [e d!]
        (try
         (let [data (js->clj (.parse js/JSON (:text store)) :keywordize-keys true)]
           (d! :data {:data data, :error nil}))
         (catch js/Error err (d! :data {:data nil, :error (.-message err)}))))})
    (=< 8 nil)
    (comp-link
     {:text "Read Cirru",
      :on-click (fn [e d!]
        (try
         (let [data (cirru-edn/parse (:text store))] (d! :data {:data data, :error nil}))
         (catch js/Error err (d! :data {:data nil, :error (.-message err)}))))})
    (=< 8 nil)
    (comment
     a
     {:inner-text "Read CSON",
      :style (merge ui/link),
      :on-click (fn [e d!]
        (try
         (let [data (CSON/parse (:text store))] (d! :data {:data data, :error nil}))
         (catch js/Error err (d! :data {:data nil, :error (.-message err)}))))})))
  (comp-input-area (:text store))))

(def display-types
  {:edn "EDN",
   :json "JSON",
   :flavored-edn "Flavored EDN",
   :cirru-edn "Cirru EDN",
   :cson "CSON"})

(defcomp
 comp-type-selector
 (current-type)
 (list->
  {}
  (->> display-types
       (map
        (fn [[k v]]
          [k
           (div
            {:style {:display :inline-block,
                     :cursor :pointer,
                     :background-color (if (= current-type k)
                       (hsl 200 80 60)
                       (hsl 200 70 88)),
                     :border-radius "16px",
                     :padding "0 16px",
                     :margin-right 8,
                     :color :white},
             :on-click (fn [e d! m!] (d! :display-type k))}
            (<> v))])))))

(defn display-data [data type]
  (case type
    :edn (with-out-str (pprint data))
    :json (.stringify js/JSON (clj->js data) nil 2)
    :flavored-edn (write-edn data {:indent 2})
    :cirru-edn (cirru-edn/write data)
    :cson (cson-stringify (clj->js data) nil 2)
    (str "Unknown type: " type)))

(defcomp
 comp-previewer
 (states store)
 (let [picker-plugin (use-prompt (>> states :picker) {:title "Pick data"})]
   (div
    {:style (merge ui/flex ui/column)}
    (div
     {:style ui/row-parted}
     (div
      {:style ui/row-middle}
      (comp-link
       {:text "Copy",
        :on-click (fn [e d!] (copy! (display-data (:data store) (:display-type store))))})
      (=< 8 nil)
      (comp-link
       {:text "Pick",
        :on-click (fn [e d!]
          ((:show picker-plugin)
           d!
           (fn [text]
             (when (not (string/blank? text))
               (d!
                :pick
                (as-> text t (string/trim t) (string/split t #"\s+") (map read-string t)))))))})
      (=< 8 nil)
      (comp-link {:text "Tidy list", :on-click (fn [e d!] (d! :tidy nil))}))
     (div
      {:style (merge ui/row-middle {:padding 8, :justify-content :flex-start})}
      (comp-type-selector (:display-type store))))
    (textarea
     {:value (display-data (:data store) (:display-type store)),
      :placeholder "Formatted edn (read only)",
      :read-only true,
      :style (merge
              ui/textarea
              ui/flex
              {:font-family ui/font-code,
               :overflow :auto,
               :white-space :pre,
               :line-height "16px",
               :font-size 12})})
    (:ui picker-plugin))))

(defcomp
 comp-container
 (reel)
 (let [store (:store reel), states (:states store)]
   (div
    {:style (merge ui/row ui/fullscreen ui/global ui/flex {:width "100%"})}
    (comp-drafter (>> states :drafter) store)
    (comp-previewer (>> states :previewer) store)
    (when config/dev? (comp-inspect "state" store {:bottom 8}))
    (when config/dev? (comp-reel (>> states :reel) reel {})))))

(defeffect
 effect-codearea
 ()
 (action el)
 (when (= action :mount)
   (if (some? el) (codearea el) (js/console.warn "Unknown target" el))))

(defn on-keydown [text]
  (fn [e d! m!]
    (if (and (= 13 (:keycode e))
             (let [event (:event e)] (or (.-metaKey event) (.-ctrlKey event))))
      (try
       (let [data (read-string text)] (d! :data {:data data, :error nil}))
       (catch js/Error err (d! :data {:data nil, :error (.-message err)}))))))
