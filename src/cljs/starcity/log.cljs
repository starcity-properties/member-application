(ns starcity.log)

(defn log [& args]
  (.apply js/console.log js/console (to-array args)))

(defn warn [& args]
  (.apply js/console.warn js/console (to-array args)))

(defn error [& args]
  (.apply js/console.error js/console (to-array args)))
