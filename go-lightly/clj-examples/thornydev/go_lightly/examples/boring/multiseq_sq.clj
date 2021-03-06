(ns thornydev.go-lightly.examples.boring.multiseq-sq
  (:require [thornydev.go-lightly.core :refer :all])
  (:import java.util.concurrent.SynchronousQueue))

(defn- boring [msg]
  (let [wait-ch (SynchronousQueue.)
        ch      (SynchronousQueue.)]
    (go (loop [i 0]
          (.put ch {:str (str msg " " i)
                    :wait wait-ch})
          (Thread/sleep (rand-int 1000))
          (.take wait-ch)
          (recur (inc i))))
    ch))

(defn- fan-in [in-chan1 in-chan2]
  (let [ch (SynchronousQueue.)]
    (doseq [inchan [in-chan1 in-chan2]]
      (go (loop []
             (.put ch (.take inchan))
             (recur))))
    ch))


(defn multiseq []
  (let [ch (fan-in (boring "Joe")
                   (boring "Ann"))]
    (dotimes [_ 5]
      (let [msg1 (.take ch)
            _    (println (:str msg1))
            msg2 (.take ch)]
        (println (:str msg2))
        (.put (:wait msg1) true)
        (.put (:wait msg2) true)))
    (println "You're both boring: I'm leaving.")
    (stop)))
