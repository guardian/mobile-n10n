-keep class com.typesafe.config.Config
-keep class scala.Function0
-keep class scala.Function1
-keep class scala.Function2
-keep class scala.Function3
-keep class scala.Option
-keep class scala.PartialFunction
-keep class scala.Predef$$less$colon$less
-keep class scala.Tuple2
-keep class scala.Tuple3
-keep class scala.collection.Iterable
-keep class scala.collection.GenIterable
-keep class scala.collection.GenSeq
-keep class scala.collection.LinearSeq
-keep class scala.collection.Seq
-keep class scala.collection.Traversable
-keep class scala.collection.TraversableOnce
-keep class scala.collection.TraversableLike
-keep class scala.collection.Iterator
-keep class scala.collection.SeqLike {
    # for SI5397
    public protected *;
}
-keep class scala.collection.generic.CanBuildFrom
-keep class scala.collection.immutable.TreeMap
-keep class scala.collection.immutable.Map
-keep class scala.collection.immutable.SortedMap
-keep class scala.collection.immutable.MapLike
-keep class scala.collection.immutable.Seq
-keep class scala.collection.immutable.TreeSet
-keep class scala.collection.immutable.Set
-keep class scala.collection.immutable.Iterable
-keep class scala.collection.immutable.IndexedSeq
-keep class scala.collection.immutable.List
-keep class scala.collection.immutable.Queue
-keep class scala.collection.immutable.Traversable
-keep class scala.collection.immutable.Vector
-keep class scala.collection.mutable.Map
-keep class scala.collection.mutable.Builder
-keep class scala.collection.mutable.Buffer
-keep class scala.collection.mutable.ArrayBuffer
-keep class scala.collection.mutable.WrappedArray
-keep class scala.collection.mutable.Queue
-keep class scala.collection.mutable.Set
-keep class scala.collection.mutable.StringBuilder
-keep class scala.concurrent.BlockContext
-keep class scala.concurrent.CanAwait
-keep class scala.concurrent.ExecutionContext
-keep class scala.concurrent.Future
-keep class scala.concurrent.Promise
-keep class scala.concurrent.forkjoin.ForkJoinPool$ForkJoinWorkerThreadFactory
-keep class scala.concurrent.forkjoin.ForkJoinPool
-keep class scala.concurrent.forkjoin.ForkJoinTask
-keep class scala.concurrent.forkjoin.ForkJoinPool$ManagedBlocker
-keep class scala.collection.GenTraversableOnce
-keep class scala.concurrent.duration.FiniteDuration
-keep class scala.concurrent.duration.Duration
-keep class scala.concurrent.duration.Deadline
-keep class scala.math.Integral
-keep class scala.math.Numeric
-keep class scala.math.Ordering
-keep class scala.reflect.ClassTag
-keep class scala.runtime.IntRef
-keep class scala.runtime.BoxedUnit
-keep class scala.runtime.IntRef
-keep class scala.runtime.ObjectRef
-keep class scala.runtime.ByteRef
-keep class scala.runtime.CharRef
-keep class scala.runtime.DoubleRef
-keep class scala.runtime.FloatRef
-keep class scala.runtime.ShortRef
-keep class scala.runtime.LongRef
-keep class scala.runtime.BooleanRef
-keep class scala.util.Try
-keep class java.io.Serializable
-keep class java.lang.Object
-keep class cats.effect.IO
-keep class cats.effect.IO$
-keep class scala.*
-keep class scala.util.Right
-keep class scala.util.Left
-keep class scala.util.Either
-keep class scala.collection.GenSeqViewLike$DroppedWhile
-keep class scala.collection.parallel.ForkJoinTasks$WrappedTask
-keep class scala.collection.parallel.FutureThreadPoolTasks$WrappedTask
-keep class scala.collection.parallel.ThreadPoolTasks$WrappedTask
-keepclassmembernames class scala.concurrent.forkjoin.ForkJoinPool {
    long ctl;
    long eventCount;
    int  indexSeed;
    int  plock;
    int  runControl;
    long stealCount;
    int  workerCounts;
}
-keepclassmembers class * {
    ** parkBlocker;
}
-keepclassmembernames class scala.concurrent.forkjoin.ForkJoinWorkerThread {
    int base;
    int sp;
    int runState;
}
-keepclassmembers class scala.concurrent.forkjoin.ForkJoinPool$WorkQueue {
    int qlock;
    int runState;
}
-keepclassmembernames class scala.concurrent.forkjoin.LinkedTransferQueue {
    int sweepVotes;
    scala.concurrent.forkjoin.LinkedTransferQueue$Node tail;
    scala.concurrent.forkjoin.LinkedTransferQueue$Node head;
}
-keepclassmembernames class scala.concurrent.forkjoin.LinkedTransferQueue$Node {
    java.lang.Object item;
    java.lang.Thread waiter;
    scala.concurrent.forkjoin.LinkedTransferQueue$Node next;
}
-keepclassmembers class scala.concurrent.forkjoin.ForkJoinTask$ExceptionNode {
    scala.concurrent.forkjoin.ForkJoinTask$ExceptionNode next;
}