(ns thornydev.go-lightly.examples.boring.generator-sq
  (:require [thornydev.go-lightly.core :refer :all])
  (:import (java.util.concurrent SynchronousQueue)))


;; ---[ Use a "sync-channel": Java SynchronousQueue ]--- ;;

(defn- boring [msg]
  (let [ch (SynchronousQueue.)]
    (go& (loop [i 0]
          (.put ch (str msg " " i))
          (Thread/sleep (rand-int 1000))
          (recur (inc i))))
    ch))


(defn single-generator []
  (let [ch (boring "boring!")]
    (dotimes [_ 5] (println "You say:" (.take ch))))
  (println "You're boring: I'm leaving."))


(defn multiple-generators []
  (let [joe (boring "Joe")
        ann (boring "Ann")]
    (dotimes [_ 10]
      (println (.take joe))
      (println (.take ann))))
  (println "You're boring: I'm leaving.")
  )
