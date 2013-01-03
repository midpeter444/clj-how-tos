(ns thornydev.go-lightly.util
  (:import (java.util.concurrent SynchronousQueue LinkedTransferQueue TimeUnit)))

;; producers should use .transfer
;; consumers should use .peek (check if anything on the queue)
;; and .take or .poll
(defn go-channel [] (LinkedTransferQueue.))

(defn sync-channel [] (SynchronousQueue.))

;; (def ^{:private true} inventory (atom []))

(def inventory (atom []))

(defmacro go
  "Launches a Clojure future as a 'go-routine' and returns the future.
   It is not necessary to keep a reference to this future, however.
   Instead, you can call the accompanying stop function to
   shutdown all futures created by this function."
  [& body]
  `(let [fut# (future ~@body)]
     (swap! inventory conj fut#)
     fut#))

(defn stop
  "Stop (cancel) all futures started via the go macro."
  []
  (doseq [f @inventory] (future-cancel f)))

(defn shutdown []
  "Stop (cancel) all futures started via the go macro and
   then call shutdown-agents to close down the entire Clojure
   agent/future thread pool."
  (stop)
  (shutdown-agents))


(defmacro go&
  "Launch a 'go-routine' like deamon Thread to execute the body. 
   This macro does not yield a future so it cannot be dereferenced.
   Instead it returns the Java Thread itself.

   It is intended to be used with go-channels for communication
   between threads.  This thread is not part of a managed Thread 
   pool so cannot be directly shutdown.  It will stop either when 
   all non-daemon threads cease or when you stop it some ad-hoc way."
  [& body]
  `(doto (Thread. (fn [] (do ~@body))) (.setDaemon true) (.start)))


(defmacro with-channel-open
  "bindings => [name init ...]

  Evaluates body in a try expression with names bound to the values
  of the inits, and a finally clause that calls (close name) on each
  name in reverse order."
  [bindings & body]
  (assert (vector? bindings) "a vector for its binding")
  (assert (even? (count bindings)) "an even number of forms in binding vector")
  (cond
    (= (count bindings) 0) `(do ~@body)
    (symbol? (bindings 0)) `(let ~(subvec bindings 0 2)
                              (try
                                (with-channel-open ~(subvec bindings 2) ~@body)
                                (finally
                                  (~'close ~(bindings 0)))))
    :else (throw (IllegalArgumentException.
                   "with-open only allows Symbols in bindings"))))



(defn- choose [ready-chans]
  (.take (nth ready-chans (rand-int (count ready-chans)))))

(defn- peek-channels [channels]
  (let [ready (doall (keep #(when-not (nil? (.peek %)) %) channels))]
    (if (seq ready)
      (nth ready (rand-int (count ready)))  ;; pick at random if >1 ready
      (Thread/sleep 0 500))))

(defn- probe-til-ready [channels]
  (loop [chans channels ready-chan nil]
    (if ready-chan
      (.take ready-chan)
      (recur channels (peek-channels channels)))))



(defn- poll-channels [channels]
  (loop [chans channels]
    (when (seq chans)
      (if-let [value (.poll (first chans) 10 TimeUnit/MICROSECONDS)]
        value
        (recur (rest chans))))))

(defn- poll-til-ready [channels]
  (loop []
    (if-let [msg (poll-channels channels)]
      msg
      (recur))))



(defn select [& channels]
  (let [ready (doall (filterv #(not (nil? (.peek %))) channels))]
    (if (seq ready)
      (choose ready)
      (probe-til-ready channels)
      ;; (poll-til-ready channels)
      )
    )
  )


;;; testing - remove later

(defn- test-routine [c n]
  (dotimes [_ 5]
    (let [value (rand-int 6000)]
      (print (str "Putting " value " on chan " n "\n")) (flush)
      (.transfer c value))))

(defn testy []
  (let [ch1 (go-channel)  ch2 (go-channel)]
    (go& (test-routine ch1 1))
    (go& (test-routine ch2 2))
    (dotimes [i 5]
      (Thread/sleep 1544)
      (print (str ">>" (select ch1 ch2) "<<\n"))
      (flush))
    )
  )
